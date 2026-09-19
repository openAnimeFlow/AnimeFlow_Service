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

/** Connections are ephemeral; the database remains the source of truth on every reconnect. */
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
    // Slow browsers never block collection workers or grow an unbounded send queue.
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
        // Periodic renewal also bounds stale connections following broker/network outages.
        var emitter = new SseEmitter(300_000L);
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
            // Register before reading: concurrent updates cannot fall into a subscribe gap.
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
                // Delivery failure must not turn a committed collection write into a failed write.
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
        // No task queries here. A write detects dead sockets; authentication uses Redis.
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
                    // Expired/revoked sessions cannot retain access through a long-lived stream.
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
