package com.ligg.accesslog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** 访问日志 Redis 写入线程池。 */
@Configuration
public class AccessLogRedisConfiguration {

    @Bean(name = "accessLogRedisExecutor")
    public Executor accessLogRedisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10_000);
        executor.setThreadNamePrefix("access-log-redis-");
        executor.initialize();
        return executor;
    }
}
