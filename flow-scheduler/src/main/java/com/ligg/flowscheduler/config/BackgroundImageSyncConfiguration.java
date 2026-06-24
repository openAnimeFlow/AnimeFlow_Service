package com.ligg.flowscheduler.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("background-image-sync-");
        executor.initialize();
        return executor;
    }
}
