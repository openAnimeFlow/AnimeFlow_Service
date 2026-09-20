package com.ligg.flowclient.service;

import com.ligg.common.constants.Constants;
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

    private final PresenceService presenceService;
    private final Set<SseEmitter> onlineCountEmitters = ConcurrentHashMap.newKeySet();
    private final Set<SseEmitter> watchingSubjectEmitters = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean snapshotDirty = new AtomicBoolean();
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

    @Override
    public void onMessage(Message message, byte[] pattern) {
        snapshotDirty.set(true);
    }

    private SseEmitter register(Set<SseEmitter> emitters) {
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT_MS);
        emitters.add(emitter);
        Runnable remove = () -> emitters.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        return emitter;
    }

    private void markSnapshotDirty() {
        if (!onlineCountEmitters.isEmpty() || !watchingSubjectEmitters.isEmpty()) {
            snapshotDirty.set(true);
        }
    }

    private void flushDirtySnapshot() {
        if (!snapshotDirty.compareAndSet(true, false)) {
            return;
        }
        if (!onlineCountEmitters.isEmpty()) {
            onlineCountEmitters.forEach(this::sendOnlineCount);
        }
        if (!watchingSubjectEmitters.isEmpty()) {
            watchingSubjectEmitters.forEach(this::sendWatchingSubjects);
        }
    }

    private void sendHeartbeats() {
        onlineCountEmitters.forEach(emitter -> sendHeartbeat(onlineCountEmitters, emitter));
        watchingSubjectEmitters.forEach(emitter -> sendHeartbeat(watchingSubjectEmitters, emitter));
    }

    private void sendOnlineCount(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                    .name("online-count")
                    .data(presenceService.onlineCount()));
        } catch (Exception error) {
            close(onlineCountEmitters, emitter);
        }
    }

    private void sendWatchingSubjects(SseEmitter emitter) {
        try {
            List<WatchingSubjectVo> subjects = presenceService.watchingSubjects();
            emitter.send(SseEmitter.event()
                    .name("watching-subjects")
                    .data(subjects));
        } catch (Exception error) {
            close(watchingSubjectEmitters, emitter);
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
        emitters.remove(emitter);
        emitter.completeWithError(new IllegalStateException("presence SSE closed"));
    }

    @PreDestroy
    void stop() {
        onlineCountEmitters.forEach(SseEmitter::complete);
        watchingSubjectEmitters.forEach(SseEmitter::complete);
        scheduler.shutdownNow();
    }
}
