package com.ligg.flowscheduler.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Bangumi Archive 同步模块的 Spring 配置：启用定时任务与异步执行，并注册专用单线程池。
 *
 * @author Ligg
 */
@Configuration
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(BangumiArchiveSyncProperties.class)
public class BangumiArchiveSyncConfiguration {

  @Bean(name = "bangumiArchiveSyncExecutor")
  public Executor bangumiArchiveSyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(1);
    executor.setThreadNamePrefix("bangumi-archive-sync-");
    executor.initialize();
    return executor;
  }
}
