package com.ligg.flowscheduler.bangumidata.scheduler;

import com.ligg.common.constants.Constants;
import com.ligg.flowscheduler.bangumidata.service.BangumiDataSyncService;
import com.ligg.flowscheduler.bangumidata.config.BangumiDataSyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BangumiDataSyncStartupRunner implements ApplicationRunner {
    private final BangumiDataSyncProperties properties;
    private final BangumiDataSyncService syncService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled() || !properties.isRunOnStartupIfMissing()) return;
        Object version = redisTemplate.opsForValue().get(Constants.BANGUMI_DATA_SYNC_VERSION_KEY);
        if (version != null && !String.valueOf(version).isBlank()) return;
        log.info("bangumi-data has not been synced; triggering startup sync");
        syncService.triggerSyncAsync();
    }
}
