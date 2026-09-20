package com.ligg.flowscheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 用户收藏同步任务历史清理配置。 */
@Data
@ConfigurationProperties(prefix = "anime-flow.collection-sync.cleanup")
public class CollectionSyncCleanupProperties {
    private boolean enabled = true;
    private int retentionDays = 30;
    private int batchSize = 100;
}
