package com.ligg.flowclient.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 弹幕发送限流：同一 access_token 在指定时间窗口内仅允许一次请求（Redis 计数）。
 * 必须在 {@link com.ligg.flowclient.interceptor.AuthorizationInterceptor} 拦截的接口上使用。
 * <p>
 * 用户标识：仅使用拦截器写入 request 的 access_token；无令牌则拒绝请求。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DanmakuSendRateLimit {

    /**
     * 时间窗口长度（秒），窗口内最多 1 次。
     */
    int seconds() default 5;
}
