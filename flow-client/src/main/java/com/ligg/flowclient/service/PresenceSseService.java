package com.ligg.flowclient.service;

import com.ligg.common.constants.Constants;
import com.ligg.flowclient.module.vo.OnlineCountVo;
import com.ligg.flowclient.module.vo.WatchingSubjectVo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 在线状态 SSE；状态变化时推送，客户端断线后会重新获取完整快照。 */
@Service
@RequiredArgsConstructor
public class PresenceSseService implements MessageListener {

    public static final String CHANNEL = Constants.PRESENCE_CHANGED_CHANNEL;

    private static final long CONNECTION_TIMEOUT_MS = 300_000L;
    private static final long FLUSH_INTERVAL_MILLIS = 250L;
    private static final long FALLBACK_REFRESH_SECONDS = 30L;
    private static final long HEARTBEAT_SECONDS = 20L;
    private static final long SNAPSHOT_CACHE_MILLIS = 1_000L;

    private final PresenceService presenceService;
    private final Set<SseEmitter> onlineCountEmitters = ConcurrentHashMap.newKeySet();
    private final Set<SseEmitter> watchingSubjectEmitters = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Integer, Set<SseEmitter>> subjectOnlineCountEmitters =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SseEmitter, String> subjectOnlineCountExclusions =
            new ConcurrentHashMap<>();
    private final Set<Integer> subjectOnlineCountSnapshotDirty = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean snapshotDirty = new AtomicBoolean();
    private final AtomicBoolean onlineCountSnapshotDirty = new AtomicBoolean(true);
    private final AtomicBoolean watchingSubjectsSnapshotDirty = new AtomicBoolean(true);
    private final Object onlineCountSnapshotLock = new Object();
    private final Object watchingSubjectsSnapshotLock = new Object();
    private volatile OnlineCountVo onlineCountSnapshot;
    private volatile long onlineCountSnapshotAt;
    private volatile List<WatchingSubjectVo> watchingSubjectsSnapshot;
    private volatile long watchingSubjectsSnapshotAt;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "presence-sse-publisher");
                thread.setDaemon(true);
                return thread;
            });

    @PostConstruct
    void start() {
        scheduler.scheduleWithFixedDelay(
                this::flushDirtySnapshot,
                FLUSH_INTERVAL_MILLIS,
                FLUSH_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(
                this::markSnapshotDirty,
                FALLBACK_REFRESH_SECONDS,
                FALLBACK_REFRESH_SECONDS,
                TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(
                this::sendHeartbeats,
                HEARTBEAT_SECONDS,
                HEARTBEAT_SECONDS,
                TimeUnit.SECONDS);
    }

    public SseEmitter subscribeOnlineCount() {
        SseEmitter emitter = register(onlineCountEmitters);
        sendOnlineCount(emitter);
        return emitter;
    }

    public SseEmitter subscribeWatchingSubjects() {
        SseEmitter emitter = register(watchingSubjectEmitters);
        sendWatchingSubjects(emitter);
        return emitter;
    }

    public SseEmitter subscribeSubjectOnlineCount(int subjectId, String excludePresenceId) {
        Set<SseEmitter> emitters = subjectOnlineCountEmitters.computeIfAbsent(
                subjectId, ignored -> ConcurrentHashMap.newKeySet());
        SseEmitter emitter = register(emitters);
        if (excludePresenceId != null && !excludePresenceId.isBlank()) {
            subjectOnlineCountExclusions.put(emitter, excludePresenceId);
        }
        subjectOnlineCountSnapshotDirty.add(subjectId);
        sendSubjectOnlineCount(emitter, subjectId, excludePresenceId);
        return emitter;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        invalidateSnapshots();
    }

    private SseEmitter register(Set<SseEmitter> emitters) {
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT_MS);
        emitters.add(emitter);
        Runnable remove = () -> {
            emitters.remove(emitter);
            subjectOnlineCountExclusions.remove(emitter);
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        return emitter;
    }

    private void markSnapshotDirty() {
        if (!onlineCountEmitters.isEmpty()
                || !watchingSubjectEmitters.isEmpty()
                || !subjectOnlineCountEmitters.isEmpty()) {
            invalidateSnapshots();
        }
    }

    private void flushDirtySnapshot() {
        if (!snapshotDirty.compareAndSet(true, false)) {
            return;
        }
        if (!onlineCountEmitters.isEmpty()) {
            sendOnlineCountSnapshot();
        }
        if (!watchingSubjectEmitters.isEmpty()) {
            sendWatchingSubjectsSnapshot();
        }
        subjectOnlineCountSnapshotDirty.removeIf(subjectId -> {
            Set<SseEmitter> emitters = subjectOnlineCountEmitters.get(subjectId);
            if (emitters == null || emitters.isEmpty()) {
                subjectOnlineCountEmitters.remove(subjectId);
                return true;
            }
            sendSubjectOnlineCountSnapshot(subjectId, emitters);
            return true;
        });
    }

    private void sendOnlineCountSnapshot() {
        final OnlineCountVo snapshot;
        try {
            snapshot = getOnlineCountSnapshot();
        } catch (Exception error) {
            snapshotDirty.set(true);
            return;
        }
        onlineCountEmitters.forEach(emitter -> sendOnlineCount(emitter, snapshot));
    }

    private void sendWatchingSubjectsSnapshot() {
        final List<WatchingSubjectVo> snapshot;
        try {
            snapshot = getWatchingSubjectsSnapshot();
        } catch (Exception error) {
            snapshotDirty.set(true);
            return;
        }
        watchingSubjectEmitters.forEach(emitter -> sendWatchingSubjects(emitter, snapshot));
    }

    private void sendHeartbeats() {
        onlineCountEmitters.forEach(emitter -> sendHeartbeat(onlineCountEmitters, emitter));
        watchingSubjectEmitters.forEach(emitter -> sendHeartbeat(watchingSubjectEmitters, emitter));
        subjectOnlineCountEmitters.forEach((subjectId, emitters) ->
                emitters.forEach(emitter -> sendHeartbeat(emitters, emitter)));
    }

    private void sendOnlineCount(SseEmitter emitter) {
        try {
            sendOnlineCount(emitter, getOnlineCountSnapshot());
        } catch (Exception error) {
            close(onlineCountEmitters, emitter);
        }
    }

    private void sendOnlineCount(SseEmitter emitter, OnlineCountVo snapshot) {
        try {
            emitter.send(SseEmitter.event()
                    .name("online-count")
                    .data(snapshot));
        } catch (Exception error) {
            close(onlineCountEmitters, emitter);
        }
    }

    private void sendWatchingSubjects(SseEmitter emitter) {
        try {
            sendWatchingSubjects(emitter, getWatchingSubjectsSnapshot());
        } catch (Exception error) {
            close(watchingSubjectEmitters, emitter);
        }
    }

    private void sendWatchingSubjects(SseEmitter emitter, List<WatchingSubjectVo> snapshot) {
        try {
            emitter.send(SseEmitter.event()
                    .name("watching-subjects")
                    .data(snapshot));
        } catch (Exception error) {
            close(watchingSubjectEmitters, emitter);
        }
    }

    private void sendSubjectOnlineCountSnapshot(
            int subjectId, Set<SseEmitter> emitters) {
        emitters.forEach(emitter -> sendSubjectOnlineCount(
                emitter,
                subjectId,
                subjectOnlineCountExclusions.get(emitter)));
    }

    private void sendSubjectOnlineCount(
            SseEmitter emitter,
            int subjectId,
            String excludePresenceId) {
        try {
            OnlineCountVo snapshot = presenceService.subjectOnlineCount(
                    subjectId, excludePresenceId);
            emitter.send(SseEmitter.event()
                    .name("subject-online-count")
                    .data(snapshot));
        } catch (Exception error) {
            Set<SseEmitter> emitters = subjectOnlineCountEmitters.get(subjectId);
            if (emitters != null) {
                close(emitters, emitter);
            }
        }
    }

    private void sendHeartbeat(Set<SseEmitter> emitters, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (Exception error) {
            close(emitters, emitter);
        }
    }

    private void close(Set<SseEmitter> emitters, SseEmitter emitter) {
        // 完成或错误回调可能会先于定时心跳发现发送失败并移除连接，
        // 因此关闭操作必须具备幂等性；客户端正常断开不应被视为服务端错误。
        if (!emitters.remove(emitter)) {
            return;
        }
        subjectOnlineCountExclusions.remove(emitter);
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // 连接可能已被其他线程并发完成。
        }
    }

    private void invalidateSnapshots() {
        snapshotDirty.set(true);
        onlineCountSnapshotDirty.set(true);
        watchingSubjectsSnapshotDirty.set(true);
        subjectOnlineCountSnapshotDirty.addAll(subjectOnlineCountEmitters.keySet());
    }

    private OnlineCountVo getOnlineCountSnapshot() {
        long now = System.currentTimeMillis();
        if (isFresh(onlineCountSnapshot, onlineCountSnapshotAt,
                onlineCountSnapshotDirty.get(), now)) {
            return onlineCountSnapshot;
        }
        synchronized (onlineCountSnapshotLock) {
            now = System.currentTimeMillis();
            if (isFresh(onlineCountSnapshot, onlineCountSnapshotAt,
                    onlineCountSnapshotDirty.get(), now)) {
                return onlineCountSnapshot;
            }
            OnlineCountVo snapshot = presenceService.onlineCount();
            onlineCountSnapshot = snapshot;
            onlineCountSnapshotAt = now;
            onlineCountSnapshotDirty.set(false);
            return snapshot;
        }
    }

    private List<WatchingSubjectVo> getWatchingSubjectsSnapshot() {
        long now = System.currentTimeMillis();
        if (isFresh(watchingSubjectsSnapshot, watchingSubjectsSnapshotAt,
                watchingSubjectsSnapshotDirty.get(), now)) {
            return watchingSubjectsSnapshot;
        }
        synchronized (watchingSubjectsSnapshotLock) {
            now = System.currentTimeMillis();
            if (isFresh(watchingSubjectsSnapshot, watchingSubjectsSnapshotAt,
                    watchingSubjectsSnapshotDirty.get(), now)) {
                return watchingSubjectsSnapshot;
            }
            List<WatchingSubjectVo> snapshot = List.copyOf(presenceService.watchingSubjects());
            watchingSubjectsSnapshot = snapshot;
            watchingSubjectsSnapshotAt = now;
            watchingSubjectsSnapshotDirty.set(false);
            return snapshot;
        }
    }

    private static boolean isFresh(Object snapshot, long snapshotAt,
                                   boolean dirty, long now) {
        return snapshot != null
                && !dirty
                && now - snapshotAt < SNAPSHOT_CACHE_MILLIS;
    }

    @PreDestroy
    void stop() {
        onlineCountEmitters.forEach(SseEmitter::complete);
        watchingSubjectEmitters.forEach(SseEmitter::complete);
        subjectOnlineCountEmitters.values().forEach(emitters -> emitters.forEach(SseEmitter::complete));
        subjectOnlineCountExclusions.clear();
        scheduler.shutdownNow();
    }
}
