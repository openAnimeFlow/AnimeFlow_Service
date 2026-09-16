package com.ligg.flowscheduler.scheduler;

import com.ligg.flowscheduler.service.ApiAccessLogSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每分钟触发一次访问日志同步，单线程串行执行。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiAccessLogSyncScheduler {

    private final ApiAccessLogSyncService apiAccessLogSyncService;

    @Value("${anime-flow.access-log.enabled:true}")
    private boolean accessLogEnabled;

    @Scheduled(
            fixedDelayString = "${anime-flow.access-log.sync.fixed-delay-ms:60000}",
            initialDelayString = "${anime-flow.access-log.sync.initial-delay-ms:60000}"
    )
    public void syncAccessLogs() {
        if (!accessLogEnabled) {
            log.debug("访问日志同步已禁用，跳过本次 Redis 消费");
            return;
        }
        log.debug("开始同步接口访问日志");
        apiAccessLogSyncService.drainQueue();
    }
}
