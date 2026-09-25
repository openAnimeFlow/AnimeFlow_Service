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

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 在线状态 SSE；状态变化时推送，客户端断线后会重新获取完整快照。
 */
@Service
@RequiredArgsConstructor
public class PresenceSseService implements MessageListener {

    public static final String CHANNEL = Constants.PRESENCE_CHANGED_CHANNEL;

    private static final long FLUSH_INTERVAL_MILLIS = 250L;
    private static final long FALLBACK_REFRESH_SECONDS = 30L;
    private static final long HEARTBEAT_SECONDS = 20L;
    private static final long SNAPSHOT_CACHE_MILLIS = 1_000L;
    private static final int MAX_CONCURRENT_DELIVERIES = 32;

    private final PresenceService presenceService;
    private final Set<SseEmitter> onlineCountEmitters = ConcurrentHashMap.newKeySet();
    private final Set<SseEmitter> watchingSubjectEmitters = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Integer, Set<SseEmitter>> subjectOnlineCountEmitters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SseEmitter, String> subjectOnlineCountExclusions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SseEmitter, Delivery> deliveries = new ConcurrentHashMap<>();
    private final Set<Integer> subjectOnlineCountSnapshotDirty = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean snapshotDirty = new AtomicBoolean();
    private final AtomicBoolean flushRunning = new AtomicBoolean();
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
    private final ExecutorService deliveryExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore deliveryPermits = new Semaphore(MAX_CONCURRENT_DELIVERIES, true);

    @PostConstruct
    void start() {
        scheduler.scheduleWithFixedDelay(
                this::scheduleFlush,
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
        SseEmitter emitter = register(onlineCountEmitters, null);
        enqueueSnapshot(emitter, target -> target.send(SseEmitter.event()
                .name("online-count")
                .data(getOnlineCountSnapshot())));
        return emitter;
    }

    public SseEmitter subscribeWatchingSubjects() {
        SseEmitter emitter = register(watchingSubjectEmitters, null);
        enqueueSnapshot(emitter, target -> target.send(SseEmitter.event()
                .name("watching-subjects")
                .data(getWatchingSubjectsSnapshot())));
        return emitter;
    }

    public SseEmitter subscribeSubjectOnlineCount(int subjectId, String excludePresenceId) {
        Set<SseEmitter> emitters = subjectOnlineCountEmitters.computeIfAbsent(
                subjectId, ignored -> ConcurrentHashMap.newKeySet());
        SseEmitter emitter = register(emitters, excludePresenceId);
        subjectOnlineCountSnapshotDirty.add(subjectId);
        enqueueSnapshot(emitter, target -> target.send(SseEmitter.event()
                .name("subject-online-count")
                .data(presenceService.subjectOnlineCount(subjectId, excludePresenceId))));
        return emitter;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        invalidateSnapshots();
    }

    private SseEmitter register(Set<SseEmitter> emitters, String excludePresenceId) {
        // A heartbeat detects dead peers; a fixed timeout would reconnect every client at once.
        SseEmitter emitter = new SseEmitter(0L);
        Delivery delivery = new Delivery(emitter, emitters);
        deliveries.put(emitter, delivery);
        if (excludePresenceId != null && !excludePresenceId.isBlank()) {
            subjectOnlineCountExclusions.put(emitter, excludePresenceId);
        }
        emitters.add(emitter);
        Runnable remove = () -> {
            emitters.remove(emitter);
            subjectOnlineCountExclusions.remove(emitter);
            delivery.closed.set(true);
            deliveries.remove(emitter, delivery);
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        return emitter;
    }

    private void scheduleFlush() {
        if (!snapshotDirty.get() || !flushRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            deliveryExecutor.execute(() -> {
                try {
                    flushDirtySnapshot();
                } catch (RuntimeException error) {
                    snapshotDirty.set(true);
                } finally {
                    flushRunning.set(false);
                }
            });
        } catch (RejectedExecutionException ignored) {
            flushRunning.set(false);
        }
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
        onlineCountEmitters.forEach(emitter -> enqueueSnapshot(emitter,
                target -> target.send(SseEmitter.event()
                        .name("online-count")
                        .data(snapshot))));
    }

    private void sendWatchingSubjectsSnapshot() {
        final List<WatchingSubjectVo> snapshot;
        try {
            snapshot = getWatchingSubjectsSnapshot();
        } catch (Exception error) {
            snapshotDirty.set(true);
            return;
        }
        watchingSubjectEmitters.forEach(emitter -> enqueueSnapshot(emitter,
                target -> target.send(SseEmitter.event()
                        .name("watching-subjects")
                        .data(snapshot))));
    }

    private void sendHeartbeats() {
        onlineCountEmitters.forEach(this::enqueueHeartbeat);
        watchingSubjectEmitters.forEach(this::enqueueHeartbeat);
        subjectOnlineCountEmitters.forEach((subjectId, emitters) ->
                emitters.forEach(this::enqueueHeartbeat));
    }

    private void sendSubjectOnlineCountSnapshot(
            int subjectId, Set<SseEmitter> emitters) {
        List<SseEmitter> subscribers = List.copyOf(emitters);
        Set<String> excludedIds = new HashSet<>();
        Map<SseEmitter, String> excludedByEmitter = new HashMap<>();
        for (SseEmitter emitter : subscribers) {
            String excludedId = subjectOnlineCountExclusions.get(emitter);
            if (excludedId != null) {
                excludedIds.add(excludedId);
                excludedByEmitter.put(emitter, excludedId);
            }
        }
        PresenceService.SubjectOnlineCounts snapshots =
                presenceService.subjectOnlineCounts(subjectId, excludedIds);
        subscribers.forEach(emitter -> enqueueSnapshot(emitter, target ->
                target.send(SseEmitter.event()
                        .name("subject-online-count")
                        .data(snapshots.forPresence(excludedByEmitter.get(emitter))))));
    }

    private void enqueueSnapshot(SseEmitter emitter, EmitterWrite write) {
        Delivery delivery = deliveries.get(emitter);
        if (delivery != null && !delivery.closed.get()) {
            // Each event is a complete snapshot, so only the newest pending one matters.
            delivery.pendingSnapshot.set(write);
            delivery.dispatch();
        }
    }

    private void enqueueHeartbeat(SseEmitter emitter) {
        Delivery delivery = deliveries.get(emitter);
        if (delivery != null && !delivery.closed.get()) {
            delivery.heartbeatPending.set(true);
            delivery.dispatch();
        }
    }

    private void close(Set<SseEmitter> emitters, SseEmitter emitter, Exception error) {
        // 完成或错误回调可能会先于定时心跳发现发送失败并移除连接，
        // 因此关闭操作必须具备幂等性；客户端正常断开不应被视为服务端错误。
        if (!emitters.remove(emitter)) {
            return;
        }
        subjectOnlineCountExclusions.remove(emitter);
        Delivery delivery = deliveries.remove(emitter);
        if (delivery != null) {
            delivery.closed.set(true);
        }
        // Servlet async processing handles an IOException from a disconnected client.
        if (!(error instanceof IOException)) {
            emitter.completeWithError(error);
        }
    }

    @FunctionalInterface
    private interface EmitterWrite {
        void send(SseEmitter emitter) throws IOException;
    }

    private final class Delivery {
        private final SseEmitter emitter;
        private final Set<SseEmitter> owner;
        private final AtomicReference<EmitterWrite> pendingSnapshot = new AtomicReference<>();
        private final AtomicBoolean heartbeatPending = new AtomicBoolean();
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();

        private Delivery(SseEmitter emitter, Set<SseEmitter> owner) {
            this.emitter = emitter;
            this.owner = owner;
        }

        private void dispatch() {
            if (running.compareAndSet(false, true)) {
                try {
                    deliveryExecutor.execute(this::drain);
                } catch (RejectedExecutionException ignored) {
                    running.set(false);
                }
            }
        }

        private void drain() {
            try {
                while (!closed.get()) {
                    EmitterWrite write = pendingSnapshot.getAndSet(null);
                    if (write == null && heartbeatPending.getAndSet(false)) {
                        write = target -> target.send(SseEmitter.event().comment("heartbeat"));
                    }
                    if (write == null) {
                        return;
                    }
                    try {
                        deliveryPermits.acquire();
                        try {
                            if (!closed.get()) {
                                write.send(emitter);
                            }
                        } finally {
                            deliveryPermits.release();
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Exception error) {
                        close(owner, emitter, error);
                        return;
                    }
                }
            } finally {
                running.set(false);
                if (!closed.get() && !Thread.currentThread().isInterrupted()
                        && (pendingSnapshot.get() != null || heartbeatPending.get())) {
                    dispatch();
                }
            }
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
        scheduler.shutdownNow();
        deliveries.values().forEach(delivery -> delivery.closed.set(true));
        onlineCountEmitters.forEach(this::completeOnShutdown);
        watchingSubjectEmitters.forEach(this::completeOnShutdown);
        subjectOnlineCountEmitters.values().forEach(emitters ->
                emitters.forEach(this::completeOnShutdown));
        subjectOnlineCountExclusions.clear();
        deliveries.clear();
        deliveryExecutor.shutdownNow();
    }

    private void completeOnShutdown(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // 应用关闭时连接可能已经被客户端或容器关闭。
        }
    }
}
