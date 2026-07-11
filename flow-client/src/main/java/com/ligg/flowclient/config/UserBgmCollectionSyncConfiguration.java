package com.ligg.flowclient.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class UserBgmCollectionSyncConfiguration {

    @Bean(name = "bgmCollectionSyncExecutor")
    public Executor bgmCollectionSyncExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("bgm-collection-sync-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(2);
        return executor;
    }
}
