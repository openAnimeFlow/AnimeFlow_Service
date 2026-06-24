package com.ligg.flowscheduler.scheduler;

import com.ligg.flowscheduler.background.BackgroundImageSyncService;
import com.ligg.flowscheduler.config.BackgroundImageSyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 背景图片定时任务入口，按 cron 触发并在后台异步执行数据同步。
 *
 * @author Ligg
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackgroundImageSyncScheduler {

    private final BackgroundImageSyncProperties properties;
    private final BackgroundImageSyncService syncService;

    @Scheduled(cron = "${anime-flow.sync.image.cron:0 30 3 * * *}")
    public void scheduleBackgroundImageSync() {
        if (!properties.isEnabled()) {
            return;
        }
        log.info("Background image scheduled sync triggered");
        syncService.triggerSyncAsync();
    }
}
