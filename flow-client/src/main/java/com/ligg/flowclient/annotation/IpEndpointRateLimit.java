package com.ligg.flowclient.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按客户端 IP 限流：在指定时间窗口内最多允许 {@link #maxRequests()} 次请求（Redis 计数）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IpEndpointRateLimit {

    /**
     * Redis 键前缀，完整键为 prefix + clientIp。
     */
    String keyPrefix();

    /**
     * 时间窗口长度（秒）。
     */
    int seconds();

    /**
     * 窗口内允许的最大请求次数。
     */
    int maxRequests() default 1;
}
