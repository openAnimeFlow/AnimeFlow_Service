package com.ligg.flowclient.interceptor;

import cn.hutool.extra.servlet.JakartaServletUtil;
import com.ligg.common.exception.RateLimitExceededException;
import com.ligg.flowclient.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;

/**
 * 按客户端 IP 限流：通过 Redis Lua 脚本在固定时间窗口内计数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IpRateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> ipRateLimitRedisScript;
    private final RateLimitProperties rateLimitProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!rateLimitProperties.isEnabled()) {
            return true;
        }

        String ip = JakartaServletUtil.getClientIP(request);
        if (!StringUtils.hasText(ip)) {
            ip = "unknown";
        }
        String key = rateLimitProperties.getKeyPrefix() + ip;
        Long allowed = stringRedisTemplate.execute(
                ipRateLimitRedisScript,
                Collections.singletonList(key),
                String.valueOf(rateLimitProperties.getMaxRequests()),
                String.valueOf(rateLimitProperties.getWindowSeconds())
        );

        if (allowed == 1L) {
            return true;
        }

        throw new RateLimitExceededException("IP<" + ip + "> 超出限流阈值");
    }
}
