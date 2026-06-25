package com.ligg.flowscheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 背景图片同步相关配置项（index.json 地址、cron 等）。
 *
 * @author Ligg
 */
@Data
@ConfigurationProperties(prefix = "anime-flow.sync.background-image")
public class BackgroundImageSyncProperties {

    /**
     * 是否启用背景图片定时同步
     */
    private boolean enabled = true;

    /**
     * 启动时若 Redis 无同步记录，则触发一次同步
     */
    private boolean runOnStartupIfMissing = true;

    /**
     * background-image/index.json 远程地址
     */
    private String indexUrl =
            "https://raw.githubusercontent.com/openAnimeFlow/animeFlow-assets/main/background-image/index.json";

    /**
     * 每日执行时间
     */
    private String cron = "0 30 3 * * *";

    /**
     * HTTP 连接超时（秒）
     */
    private int connectTimeoutSeconds = 15;

    /**
     * HTTP 读取超时（秒）
     */
    private int readTimeoutSeconds = 30;
}
