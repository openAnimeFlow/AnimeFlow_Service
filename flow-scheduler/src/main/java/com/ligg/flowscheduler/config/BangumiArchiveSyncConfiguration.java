package com.ligg.flowscheduler.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

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
    SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("bangumi-archive-sync-");
    executor.setVirtualThreads(true);
    executor.setConcurrencyLimit(1);
    return executor;
  }
}
