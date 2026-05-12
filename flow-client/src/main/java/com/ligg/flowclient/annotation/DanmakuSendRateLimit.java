package com.ligg.flowclient.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 弹幕发送限流：同一用户在同一 {@code bgmId} 下，在指定时间窗口内仅允许一次请求（Redis 计数）。
 * <p>
 * 用户标识：有 {@code Authorization: Bearer &lt;token&gt;} 时使用原始 token + bgmId；无令牌时仅按 bgmId（不按 IP）。
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
