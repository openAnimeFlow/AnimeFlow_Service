package com.ligg.flowscheduler.bangumidata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** bangumi-data 数据集同步配置。 */
@Data
@ConfigurationProperties(prefix = "anime-flow.sync.bangumi-data")
public class BangumiDataSyncProperties {

    private boolean enabled = true;
    private boolean runOnStartupIfMissing = true;
    private String dataUrl = "https://raw.githubusercontent.com/bangumi-data/bangumi-data/master/dist/data.json";
    private String cron = "0 15 4 * * *";
    private int batchSize = 300;
    private long lockTtlSeconds = 1800L;
    private int connectTimeoutSeconds = 20;
    private int readTimeoutSeconds = 180;
}
