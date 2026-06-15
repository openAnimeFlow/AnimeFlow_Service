package com.ligg.flowscheduler.archive;

import com.ligg.common.constants.Constants;
import com.ligg.flowscheduler.archive.dto.ArchiveAssetDto;
import com.ligg.flowscheduler.archive.dto.ArchiveLatestDto;
import com.ligg.flowscheduler.config.BangumiArchiveSyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Bangumi Archive 同步编排：检查更新、分布式锁、下载资源并按类型顺序导入数据库。
 *
 * @author Ligg
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BangumiArchiveSyncService {

    private final BangumiArchiveSyncProperties properties;
    private final BangumiArchiveHttpService httpService;
    private final BangumiArchiveJsonlinesSyncService jsonlinesSyncService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Async("bangumiArchiveSyncExecutor")
    public void triggerSyncAsync() {
        try {
            syncIfNeeded();
        } catch (Exception e) {
            log.error("Bangumi archive sync failed", e);
        }
    }

    public void syncIfNeeded() throws Exception {
        if (!properties.isEnabled()) {
            log.debug("Bangumi archive sync is disabled");
            return;
        }

        ArchiveLatestDto latest = httpService.fetchLatest();
        String remoteUpdatedAt = latest.getSourceUpdatedAt();
        if (remoteUpdatedAt == null || remoteUpdatedAt.isBlank()) {
            log.warn("latest.json missing source_updated_at, skip sync");
            return;
        }

        Object cached = redisTemplate.opsForValue().get(Constants.BANGUMI_ARCHIVE_SYNC_UPDATED_AT_KEY);
        if (cached != null && remoteUpdatedAt.equals(String.valueOf(cached))) {
            log.info("Bangumi archive is up to date: {}", remoteUpdatedAt);
            return;
        }

        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                Constants.BANGUMI_ARCHIVE_SYNC_LOCK_KEY,
                remoteUpdatedAt,
                Duration.ofSeconds(properties.getLockTtlSeconds()));
        if (!Boolean.TRUE.equals(locked)) {
            log.info("Bangumi archive sync already running, skip");
            return;
        }

        try {
            log.info("Starting Bangumi archive sync, source_updated_at={}", remoteUpdatedAt);
            runSync(latest);
            redisTemplate.opsForValue().set(Constants.BANGUMI_ARCHIVE_SYNC_UPDATED_AT_KEY, remoteUpdatedAt);
            log.info("Bangumi archive sync completed, source_updated_at={}", remoteUpdatedAt);
        } finally {
            redisTemplate.delete(Constants.BANGUMI_ARCHIVE_SYNC_LOCK_KEY);
        }
    }

    private void runSync(ArchiveLatestDto latest) throws Exception {
        String dumpName = latest.getDumpName();
        if (dumpName == null || dumpName.isBlank()) {
            throw new IllegalStateException("latest.json missing dump_name");
        }
        List<ArchiveAssetDto> assets = latest.getAssets();
        if (assets == null || assets.isEmpty()) {
            throw new IllegalStateException("latest.json missing assets");
        }

        Path workDir = Path.of(properties.getDownloadDir(), dumpName);
        Files.createDirectories(workDir);

        assets.stream()
                .map(asset -> ArchiveDataType.fromAssetName(asset.getName(), dumpName)
                        .map(type -> new AssetJob(type, asset))
                        .orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(AssetJob::type, ArchiveDataType.orderComparator()))
                .forEach(job -> syncAsset(workDir, job));

        cleanupWorkDir(workDir);
    }

    private void syncAsset(Path workDir, AssetJob job) {
        ArchiveAssetDto asset = job.asset();
        ArchiveDataType dataType = job.type();
        Path localFile = workDir.resolve(asset.getName());
        try {
            httpService.download(asset.getBrowserDownloadUrl(), localFile);
            jsonlinesSyncService.syncFile(localFile, dataType);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to sync asset " + asset.getName() + ": " + e.getMessage(), e);
        } finally {
            try {
                Files.deleteIfExists(localFile);
            } catch (Exception e) {
                log.warn("Failed to delete temp file {}", localFile, e);
            }
        }
    }

    private void cleanupWorkDir(Path workDir) {
        try {
            if (Files.isDirectory(workDir)) {
                Files.list(workDir).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                        // ignore
                    }
                });
                Files.deleteIfExists(workDir);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup work dir {}", workDir, e);
        }
    }

    private record AssetJob(ArchiveDataType type, ArchiveAssetDto asset) {
    }
}
