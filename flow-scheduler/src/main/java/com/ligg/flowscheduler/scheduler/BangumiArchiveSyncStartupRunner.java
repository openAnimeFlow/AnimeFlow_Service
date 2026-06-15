package com.ligg.flowscheduler.scheduler;

import com.ligg.common.constants.Constants;
import com.ligg.flowscheduler.archive.BangumiArchiveSyncService;
import com.ligg.flowscheduler.config.BangumiArchiveSyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 应用启动后检查 Redis 是否已有同步记录，若无则触发一次 Bangumi Archive 同步。
 *
 * @author Ligg
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BangumiArchiveSyncStartupRunner implements ApplicationRunner {

    private final BangumiArchiveSyncProperties properties;
    private final BangumiArchiveSyncService syncService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled() || !properties.isRunOnStartupIfMissing()) {
            return;
        }

        Object cached = redisTemplate.opsForValue().get(Constants.BANGUMI_ARCHIVE_SYNC_UPDATED_AT_KEY);
        if (cached != null && !String.valueOf(cached).isBlank()) {
            log.info("Bangumi archive already synced ({}), skip startup sync", cached);
            return;
        }

        log.info(
                "Redis key {} not found, triggering Bangumi archive startup sync",
                Constants.BANGUMI_ARCHIVE_SYNC_UPDATED_AT_KEY);
        syncService.triggerSyncAsync();
    }
}
