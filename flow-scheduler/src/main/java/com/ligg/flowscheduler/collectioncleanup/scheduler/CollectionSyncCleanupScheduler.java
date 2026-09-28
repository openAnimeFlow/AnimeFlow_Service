package com.ligg.flowscheduler.collectioncleanup.scheduler;

import com.ligg.flowscheduler.collectioncleanup.config.CollectionSyncCleanupProperties;
import com.ligg.flowscheduler.collectioncleanup.service.CollectionSyncCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 触发已过保留期的收藏同步任务清理。 */
@Component
@RequiredArgsConstructor
public class CollectionSyncCleanupScheduler {
    private final CollectionSyncCleanupProperties properties;
    private final CollectionSyncCleanupService cleanupService;

    @Scheduled(
            initialDelayString = "${anime-flow.collection-sync.cleanup.initial-delay-ms:300000}",
            fixedDelayString = "${anime-flow.collection-sync.cleanup.interval-ms:86400000}")
    public void cleanupExpiredTasks() {
        if (properties.isEnabled()) cleanupService.cleanupExpiredTasks();
    }
}
