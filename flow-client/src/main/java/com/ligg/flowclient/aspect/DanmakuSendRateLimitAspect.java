package com.ligg.flowclient.aspect;

import com.ligg.common.exception.AuthorizationException;
import com.ligg.common.exception.RateLimitExceededException;
import com.ligg.flowclient.annotation.DanmakuSendRateLimit;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;

/**
 * {@link DanmakuSendRateLimit}：固定窗口限流（复用 IP 限流 Lua 脚本）。
 * 按 {@link AuthorizationInterceptor} 写入的 access_token 区分用户；无有效 token 直接拒绝。
 */
@Aspect
@Component
@Order(0)
@RequiredArgsConstructor
public class DanmakuSendRateLimitAspect {

    private static final String KEY_PREFIX = "animeflow:danmaku:send:";

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> ipRateLimitRedisScript;

    @Around("@annotation(limit)")
    public Object around(ProceedingJoinPoint pjp, DanmakuSendRateLimit limit) throws Throwable {
        HttpServletRequest request = currentRequest();
        String key = KEY_PREFIX + requireTokenKeyPart(request);

        Long allowed = stringRedisTemplate.execute(
                ipRateLimitRedisScript,
                Collections.singletonList(key),
                "1",
                String.valueOf(limit.seconds())
        );
        if (allowed != 1L) {
            throw new RateLimitExceededException("弹幕发送过于频繁，请稍后再试");
        }
        return pjp.proceed();
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    /**
     * {@code t:} + 拦截器解析后的 access_token；无请求上下文或无 token 则拒绝。
     */
    private static String requireTokenKeyPart(HttpServletRequest request) {
        if (request == null) {
            throw new AuthorizationException("缺少或无效的访问令牌");
        }
        Object tokenAttr = request.getAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE);
        if (tokenAttr instanceof String token && StringUtils.hasText(token)) {
            return "t:" + token;
        }
        throw new AuthorizationException("缺少或无效的访问令牌");
    }
}
