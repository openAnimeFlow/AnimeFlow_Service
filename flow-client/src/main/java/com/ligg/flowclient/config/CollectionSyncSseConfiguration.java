package com.ligg.flowclient.config;

import com.ligg.flowclient.service.CollectionSyncSseService;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class CollectionSyncSseConfiguration {
    // Preserve Boot's configured scheduler for existing @Scheduled recovery work.
    @Bean(name = "taskScheduler")
    @ConditionalOnMissingBean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }

    @Bean
    public ThreadPoolTaskScheduler collectionSyncSseScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("collection-sse-schedule-");
        return scheduler;
    }

    @Bean
    public RedisMessageListenerContainer collectionSyncListener(RedisConnectionFactory factory,
                                                                CollectionSyncSseService streams) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(streams, new ChannelTopic(CollectionSyncSseService.CHANNEL));
        return container;
    }
}
