package com.ligg.flowclient.service;

import com.ligg.flowclient.module.vo.UserBgmCollectionSyncStatusVo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.*;

/** SSE 连接是临时的；每次重连都以数据库中的状态为准。 */
@Service
@Slf4j
public class CollectionSyncSseService implements MessageListener {
    public static final String CHANNEL = "animeflow:collection-sync:changed";
    private final CollectionSyncTaskService tasks;
    private final JwtTokenService jwt;
    private final StringRedisTemplate redis;
    private final ConcurrentMap<Long, Set<Connection>> connections = new ConcurrentHashMap<>();
    private final Set<Long> dirty = ConcurrentHashMap.newKeySet();
    private final Set<Long> broadcasts = ConcurrentHashMap.newKeySet();
    // 较慢的客户端不会阻塞收藏任务，也不会导致发送队列无限增长。
    private final ExecutorService sends = new ThreadPoolExecutor(2, 8, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(256), runnable -> {
                Thread thread = new Thread(runnable, "collection-sse-send");
                thread.setDaemon(true);
                return thread;
            });

    public CollectionSyncSseService(CollectionSyncTaskService tasks, JwtTokenService jwt,
                                    StringRedisTemplate redis) {
        this.tasks = tasks;
        this.jwt = jwt;
        this.redis = redis;
    }

    public SseEmitter subscribe(Long userId, String accessToken) {
        // 定期续期也能限制消息代理或网络故障后残留连接的存活时间。
        // Heartbeats keep the connection and intermediaries alive; do not let
        // Spring's async request timeout close a healthy SSE stream every five minutes.
        var emitter = new SseEmitter(0L);
        var connection = new Connection(userId, accessToken, emitter);
        connections.compute(userId, (id, existing) -> {
            Set<Connection> set = existing == null ? ConcurrentHashMap.newKeySet() : existing;
            set.add(connection);
            return set;
        });
        emitter.onCompletion(connection::close);
        emitter.onTimeout(connection::close);
        emitter.onError(error -> connection.close());
        try {
            // 先注册再读取状态，避免并发更新落入订阅空窗期。
            connection.offer(tasks.status(userId));
        } catch (RuntimeException error) {
            connection.close();
            throw error;
        }
        return emitter;
    }

    @EventListener
    public void changed(CollectionSyncEvents.Changed event) {
        broadcasts.add(event.userId());
        markDirty(event.userId());
    }

    private void markDirty(Long userId) {
        if (connections.containsKey(userId)) dirty.add(userId);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            markDirty(Long.valueOf(new String(message.getBody(), StandardCharsets.UTF_8)));
        } catch (NumberFormatException ignored) {
            log.warn("Invalid collection SSE notification");
        }
    }

    @Scheduled(fixedDelay = 1000, scheduler = "collectionSyncSseScheduler")
    public void flushChanges() {
        for (Long userId : broadcasts.toArray(Long[]::new)) {
            if (!broadcasts.remove(userId)) continue;
            try {
                redis.convertAndSend(CHANNEL, userId.toString());
            } catch (RuntimeException error) {
                // 消息投递失败不能让已经提交的收藏写入变成失败写入。
                log.warn("Collection SSE broadcast unavailable; clients recover by reconnecting");
                closeUser(userId);
            }
        }
        for (Long userId : dirty.toArray(Long[]::new)) {
            if (!dirty.remove(userId)) continue;
            var subscribers = connections.get(userId);
            if (subscribers == null) continue;
            try {
                var snapshot = tasks.status(userId);
                subscribers.forEach(connection -> connection.offer(snapshot));
            } catch (RuntimeException error) {
                closeUser(userId);
            }
        }
    }

    @Scheduled(fixedDelay = 20_000, scheduler = "collectionSyncSseScheduler")
    public void heartbeat() {
        // 这里不查询任务状态；写入操作负责发现失效连接，认证通过 Redis 完成。
        connections.values().forEach(set -> set.forEach(Connection::heartbeat));
    }

    private void closeUser(Long userId) {
        var set = connections.get(userId);
        if (set != null) set.forEach(Connection::close);
    }

    @PreDestroy
    public void close() {
        connections.values().forEach(set -> set.forEach(Connection::close));
        sends.shutdownNow();
    }

    private static int compare(UserBgmCollectionSyncStatusVo a, UserBgmCollectionSyncStatusVo b) {
        long aId = a.getTaskId() == null ? -1 : a.getTaskId();
        long bId = b.getTaskId() == null ? -1 : b.getTaskId();
        int task = Long.compare(aId, bId);
        return task != 0 ? task : Long.compare(a.getStatusVersion() == null ? -1 : a.getStatusVersion(),
                b.getStatusVersion() == null ? -1 : b.getStatusVersion());
    }

    private final class Connection {
        final Long userId;
        final String token;
        final SseEmitter emitter;
        UserBgmCollectionSyncStatusVo newest;
        UserBgmCollectionSyncStatusVo pending;
        boolean pulse, sending, closed;

        Connection(Long userId, String token, SseEmitter emitter) {
            this.userId = userId; this.token = token; this.emitter = emitter;
        }

        synchronized void offer(UserBgmCollectionSyncStatusVo snapshot) {
            if (closed || (newest != null && compare(snapshot, newest) <= 0)) return;
            newest = snapshot;
            pending = snapshot;
            dispatch();
        }

        synchronized void heartbeat() {
            if (closed) return;
            pulse = true;
            dispatch();
        }

        void dispatch() {
            if (sending) return;
            sending = true;
            try { sends.execute(this::drain); }
            catch (RejectedExecutionException busy) { close(); }
        }

        void drain() {
            try {
                while (true) {
                    UserBgmCollectionSyncStatusVo snapshot;
                    boolean sendPulse;
                    synchronized (this) {
                        if (closed) return;
                        snapshot = pending; pending = null;
                        sendPulse = pulse; pulse = false;
                        if (snapshot == null && !sendPulse) { sending = false; return; }
                    }
                    // 过期或撤销的会话不能通过长期连接继续保留访问权限。
                    if (!userId.equals(jwt.validateAccessToken(token))) { close(); return; }
                    if (snapshot != null) {
                        emitter.send(SseEmitter.event().name("status")
                                .id(snapshot.getTaskId() + ":" + snapshot.getStatusVersion()).data(snapshot));
                    } else {
                        emitter.send(SseEmitter.event().comment("heartbeat"));
                    }
                }
            } catch (Exception error) {
                close();
            }
        }

        synchronized void close() {
            if (closed) return;
            closed = true;
            pending = null;
            connections.computeIfPresent(userId, (id, set) -> {
                set.remove(this);
                return set.isEmpty() ? null : set;
            });
            emitter.complete();
        }
    }
}
