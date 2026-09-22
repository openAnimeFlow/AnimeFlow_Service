package com.ligg.flowclient.config;

import com.ligg.flowclient.service.PresenceSseService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Presence SSE 的 Redis Pub/Sub 监听配置。 */
@Configuration
public class PresenceSseConfiguration {

    @Bean
    public RedisMessageListenerContainer presenceSseListener(
            RedisConnectionFactory factory,
            PresenceSseService presenceSseService) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(
                presenceSseService,
                new ChannelTopic(PresenceSseService.CHANNEL));
        return container;
    }
}
