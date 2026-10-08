package com.ligg.flowscheduler.archive.config;

import com.ligg.flowscheduler.archive.ArchiveDataType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * Bangumi Archive 定时同步相关配置项（latest.json 地址、cron、批次大小等）。
 *
 * @author Ligg
 */
@Data
@ConfigurationProperties(prefix = "anime-flow.sync.archive")
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
     * 未单独配置类型时使用的每批 upsert 行数
     */
    private int batchSize = 500;

    /** 各类型批次大小，大文本表使用较小批次。 */
    private Map<ArchiveDataType, Integer> batchSizes = defaultBatchSizes();

    /** 标签缓冲区最多保留的实体数，与 subject 批次大小独立。 */
    private int tagBatchSize = 500;

    /**
     * 每批估算数据量上限（字节）。读取阶段按 JSON 行的 UTF-16 大小加固定开销估算，
     * 索引阶段按生成字段估算；不是精确堆占用限制，超大单条记录独立写入。
     */
    private long maxBatchBytes = 1024 * 1024;

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

    private static Map<ArchiveDataType, Integer> defaultBatchSizes() {
        Map<ArchiveDataType, Integer> sizes = new EnumMap<>(ArchiveDataType.class);
        sizes.put(ArchiveDataType.SUBJECT, 100);
        sizes.put(ArchiveDataType.CHARACTER, 100);
        sizes.put(ArchiveDataType.PERSON, 100);
        sizes.put(ArchiveDataType.EPISODE, 200);
        return sizes;
    }
}
