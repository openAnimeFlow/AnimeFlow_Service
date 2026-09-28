package com.ligg.flowscheduler.bangumidata.scheduler;

import com.ligg.flowscheduler.bangumidata.service.BangumiDataSyncService;
import com.ligg.flowscheduler.bangumidata.config.BangumiDataSyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BangumiDataSyncScheduler {
    private final BangumiDataSyncProperties properties;
    private final BangumiDataSyncService syncService;

    @Scheduled(cron = "${anime-flow.sync.bangumi-data.cron:0 15 4 * * *}")
    public void scheduleSync() {
        if (!properties.isEnabled()) return;
        log.info("bangumi-data scheduled sync triggered");
        syncService.triggerSyncAsync();
    }
}
