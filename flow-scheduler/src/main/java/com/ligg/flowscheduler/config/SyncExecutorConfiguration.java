package com.ligg.flowscheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** 同步任务专用线程池配置。每类同步任务使用独立的单线程池，互不阻塞。 */
@Configuration
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties({
        BangumiArchiveSyncProperties.class,
        BackgroundImageSyncProperties.class,
        ApiAccessLogArchiveProperties.class,
        CollectionSyncCleanupProperties.class
})
public class SyncExecutorConfiguration {

    @Bean(name = "bangumiArchiveSyncExecutor")
    public Executor bangumiArchiveSyncExecutor() {
        return createExecutor("bangumi-archive-sync-");
    }

    @Bean(name = "backgroundImageSyncExecutor")
    public Executor backgroundImageSyncExecutor() {
        return createExecutor("background-image-sync-");
    }

    private ThreadPoolTaskExecutor createExecutor(String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }
}
