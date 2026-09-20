package com.ligg.flowscheduler.service;

import com.ligg.flowscheduler.config.CollectionSyncCleanupProperties;
import com.ligg.flowscheduler.mapper.CollectionSyncCleanupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 清理已过保留期的终态同步任务及其明细。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CollectionSyncCleanupService {
    private static final int MIN_RETENTION_DAYS = 1;
    private static final int MAX_BATCH_SIZE = 1_000;

    private final CollectionSyncCleanupMapper tasks;
    private final CollectionSyncCleanupProperties properties;

    @Scheduled(
            initialDelayString = "${anime-flow.collection-sync.cleanup.initial-delay-ms:300000}",
            fixedDelayString = "${anime-flow.collection-sync.cleanup.interval-ms:86400000}")
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredTasks() {
        if (!properties.isEnabled()) return;
        int retentionDays = Math.max(properties.getRetentionDays(), MIN_RETENTION_DAYS);
        int batchSize = Math.min(Math.max(properties.getBatchSize(), 1), MAX_BATCH_SIZE);
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        var taskIds = tasks.selectExpiredTaskIds(before, batchSize);
        int deletedTasks = 0;
        int deletedItems = 0;
        for (Long taskId : taskIds) {
            deletedItems += tasks.deleteItemsByTaskId(taskId);
            deletedTasks += tasks.deleteExpiredTask(taskId, before);
        }
        if (deletedTasks > 0) {
            log.info("清理收藏同步历史任务 taskCount={} itemCount={} retentionDays={}",
                    deletedTasks, deletedItems, retentionDays);
        }
    }
}
