package com.ligg.flowscheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bangumi Archive 定时同步相关配置项（latest.json 地址、cron、批次大小等）。
 *
 * @author Ligg
 */
@Data
@ConfigurationProperties(prefix = "anime-flow.bangumi-archive-sync")
public class BangumiArchiveSyncProperties {

    /**
     * 是否启用 Bangumi Archive 定时同步
     */
    private boolean enabled = true;

    /**
     * 启动时若 Redis 无 source_updated_at 记录，则触发一次同步（首次部署场景）
     */
    private boolean runOnStartupIfMissing = true;

    /**
     * archive/latest.json 地址
     */
    private String latestUrl =
            "https://raw.githubusercontent.com/openAnimeFlow/animeFlow-assets/main/archive/latest.json";

    /**
     * 每日执行时间（cron），默认凌晨 03:00
     */
    private String cron = "0 0 3 * * *";

    /**
     * 每批 upsert 行数
     */
    private int batchSize = 500;

    /**
     * 批次之间的休眠毫秒数，降低对线上数据库的压力
     */
    private long batchDelayMs = 50L;

    /**
     * 下载文件临时目录
     */
    private String downloadDir = System.getProperty("java.io.tmpdir") + "/bangumi-archive";

    /**
     * 同步任务分布式锁最长持有时间（秒）
     */
    private long lockTtlSeconds = 6 * 3600L;

    /**
     * HTTP 连接超时（秒）
     */
    private int connectTimeoutSeconds = 30;

    /**
     * HTTP 读取超时（秒），大文件下载需较长
     */
    private int readTimeoutSeconds = 3600;
}
