package com.ligg.flowclient.aspect;

import cn.hutool.extra.servlet.JakartaServletUtil;
import com.ligg.common.exception.RateLimitExceededException;
import com.ligg.flowclient.annotation.IpEndpointRateLimit;
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
 * {@link IpEndpointRateLimit}：按客户端 IP 固定窗口限流（复用 IP 限流 Lua 脚本）。
 */
@Aspect
@Component
@Order(0)
@RequiredArgsConstructor
public class IpEndpointRateLimitAspect {

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> ipRateLimitRedisScript;

    @Around("@annotation(limit)")
    public Object around(ProceedingJoinPoint pjp, IpEndpointRateLimit limit) throws Throwable {
        HttpServletRequest request = currentRequest();
        String ip = resolveClientIp(request);
        String key = limit.keyPrefix() + ip;

        Long allowed = stringRedisTemplate.execute(
                ipRateLimitRedisScript,
                Collections.singletonList(key),
                String.valueOf(limit.maxRequests()),
                String.valueOf(limit.seconds())
        );
        if (allowed != 1L) {
            throw new RateLimitExceededException("请求过于频繁，请稍后再试");
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

    private static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = JakartaServletUtil.getClientIP(request);
        return StringUtils.hasText(ip) ? ip : "unknown";
    }
}
