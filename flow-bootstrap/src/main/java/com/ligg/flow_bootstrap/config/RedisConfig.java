package com.ligg.flow_bootstrap.config;

import com.ligg.common.utils.RedisJsonMapper;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 应用级 Redis 配置。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        JacksonJsonRedisSerializer<@NonNull Object> serializer =
                new JacksonJsonRedisSerializer<>(RedisJsonMapper.create(), Object.class);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Redis 字符串模板统一使用 UTF-8 字符串序列化。
     *
     * <p>限流、Lua 脚本、队列和 Presence 索引都使用该模板；不要让这些
     * Redis 数据被对象 JSON 序列化器包装，否则会影响脚本参数和跨模块读取。</p>
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate(factory);
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public DefaultRedisScript<@NonNull Long> ipRateLimitRedisScript() {
        DefaultRedisScript<@NonNull Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/ip_rate_limit.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
