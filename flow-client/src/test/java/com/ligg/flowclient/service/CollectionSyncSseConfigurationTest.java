package com.ligg.flowclient.service;

import com.ligg.flowclient.config.CollectionSyncSseConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CollectionSyncSseConfigurationTest {
    @Test void heartbeatSchedulerIsIsolatedFromExistingRecoveryScheduler() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(CollectionSyncSseConfiguration.class)
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean(CollectionSyncSseService.class, () -> mock(CollectionSyncSseService.class))
                .withBean(BeanPostProcessor.class, () -> new BeanPostProcessor() {
                    @Override public Object postProcessBeforeInitialization(Object bean, String name) {
                        // No real Redis dependency for the wiring/scheduler isolation test.
                        if (bean instanceof RedisMessageListenerContainer) return mock(RedisMessageListenerContainer.class);
                        return bean;
                    }
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean("taskScheduler"))
                            .isNotSameAs(context.getBean("collectionSyncSseScheduler"));
                    assertThat(context.getBean("collectionSyncSseScheduler", ThreadPoolTaskScheduler.class)
                            .getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(2);
                });
    }
}
