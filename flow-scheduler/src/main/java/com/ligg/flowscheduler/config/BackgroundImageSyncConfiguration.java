package com.ligg.flowscheduler.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 背景图片同步模块的 Spring 配置：注册专用单线程池。
 *
 * @author Ligg
 */
@Configuration
@EnableConfigurationProperties(BackgroundImageSyncProperties.class)
public class BackgroundImageSyncConfiguration {

    @Bean(name = "backgroundImageSyncExecutor")
    public Executor backgroundImageSyncExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("background-image-sync-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(1);
        return executor;
    }
}
