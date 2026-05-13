package com.ligg.flowclient.aspect;

import com.ligg.common.exception.RateLimitExceededException;
import com.ligg.flowclient.annotation.DanmakuSendRateLimit;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.module.dto.DanmakuDto;
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
 * 已登录：按 {@link AuthorizationInterceptor} 写入的 access_token + bgmId；无 token 时仅按 bgmId。
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
        DanmakuDto dto = findDanmakuDto(pjp.getArgs());
        if (dto == null || dto.getBgmId() == null) {
            return pjp.proceed();
        }

        HttpServletRequest request = currentRequest();
        String userPart = resolveUserPart(request);
        String key = KEY_PREFIX + userPart + ":" + dto.getBgmId();

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

    private static DanmakuDto findDanmakuDto(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof DanmakuDto d) {
                return d;
            }
        }
        return null;
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    /**
     * 已登录：{@code t:} + 拦截器解析后的 access_token；否则 {@code anon}（与请求体中的 bgmId 组合）。
     */
    private static String resolveUserPart(HttpServletRequest request) {
        if (request == null) {
            return "anon";
        }
        Object tokenAttr = request.getAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE);
        if (tokenAttr instanceof String token && StringUtils.hasText(token)) {
            return "t:" + token;
        }
        return "anon";
    }
}
