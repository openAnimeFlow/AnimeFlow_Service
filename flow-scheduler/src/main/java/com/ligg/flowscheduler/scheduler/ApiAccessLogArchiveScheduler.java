package com.ligg.flowscheduler.scheduler;

import com.ligg.flowscheduler.service.ApiAccessLogArchiveService;
import com.ligg.flowscheduler.config.ApiAccessLogArchiveProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每月 1 日归档上月接口访问日志。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiAccessLogArchiveScheduler {

    private final ApiAccessLogArchiveProperties properties;
    private final ApiAccessLogArchiveService archiveService;

    @Scheduled(
            cron = "${anime-flow.access-log.archive.cron:0 0 2 1 * *}",
            zone = "${anime-flow.access-log.archive.zone:Asia/Shanghai}"
    )
    public void archivePreviousMonth() {
        if (!properties.isEnabled()) {
            return;
        }
        log.info("开始归档上月接口访问日志");
        archiveService.archivePreviousMonth();
    }
}
