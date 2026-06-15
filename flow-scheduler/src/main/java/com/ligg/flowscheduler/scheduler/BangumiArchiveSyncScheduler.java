package com.ligg.flowscheduler.scheduler;

import com.ligg.flowscheduler.archive.BangumiArchiveSyncService;
import com.ligg.flowscheduler.config.BangumiArchiveSyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bangumi Archive 定时任务入口，按 cron 触发并在后台异步执行数据同步。
 *
 * @author Ligg
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BangumiArchiveSyncScheduler {

    private final BangumiArchiveSyncProperties properties;
    private final BangumiArchiveSyncService syncService;

    @Scheduled(cron = "${anime-flow.bangumi-archive-sync.cron:0 0 3 * * *}")
    public void scheduleBangumiArchiveSync() {
        if (!properties.isEnabled()) {
            return;
        }
        log.info("Bangumi archive scheduled sync triggered");
        syncService.triggerSyncAsync();
    }
}
