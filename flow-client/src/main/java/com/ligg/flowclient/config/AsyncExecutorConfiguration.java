package com.ligg.flowclient.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncExecutorConfiguration {

    @Bean(name = "bgmCollectionSyncExecutor")
    public ThreadPoolTaskExecutor bgmCollectionSyncExecutor() {
        return createExecutor(1, 2, 50, "bgm-collection-sync-");
    }

    @Bean(name = "subjectImageRefreshExecutor")
    public ThreadPoolTaskExecutor subjectImageRefreshExecutor() {
        return createExecutor(2, 4, 100, "subject-image-refresh-");
    }

    private ThreadPoolTaskExecutor createExecutor(int corePoolSize, int maxPoolSize,
                                                 int queueCapacity, String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        // 队列满时拒绝任务，避免远程调用退回请求线程执行。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 作为 Spring Bean，由容器统一初始化并在关闭时销毁。
        return executor;
    }
}
