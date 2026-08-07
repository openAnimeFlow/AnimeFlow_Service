package com.ligg.accesslog.filter;

import com.ligg.accesslog.config.AccessLogProperties;
import com.ligg.common.entity.ApiAccessLogEntity;
import com.ligg.accesslog.service.ApiAccessLogRedisProducer;
import com.ligg.common.utils.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 记录所有 HTTP 请求的访问日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiAccessLogFilter extends OncePerRequestFilter {

    private static final Pattern SENSITIVE_QUERY_KEY = Pattern.compile(
            "(?i)^(password|passwd|token|access_token|refresh_token|authorization|secret|code)$");

    private final ApiAccessLogRedisProducer apiAccessLogRedisProducer;
    private final AccessLogProperties accessLogProperties;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!accessLogProperties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        long startNanos = System.nanoTime();
        String traceId = resolveTraceId(request);
        Throwable failure = null;

        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            saveQuietly(request, response, traceId, startNanos, failure);
        }
    }

    private void saveQuietly(
            HttpServletRequest request,
            HttpServletResponse response,
            String traceId,
            long startNanos,
            Throwable failure
    ) {
        try {
            ApiAccessLogEntity entity = new ApiAccessLogEntity();
            entity.setTraceId(traceId);
            entity.setRequestTime(LocalDateTime.now());
            entity.setMethod(request.getMethod());
            entity.setUri(request.getRequestURI());
            entity.setRoute(resolveRoute(request));
            entity.setQueryString(sanitizeQuery(request.getQueryString()));
            entity.setClientIp(ClientIpResolver.resolve(request));
            entity.setUserAgent(limit(request.getHeader("User-Agent"), 512));
            entity.setReferer(limit(request.getHeader("Referer"), 512));
            entity.setHttpStatus(response.getStatus());
            entity.setSuccess(failure == null && response.getStatus() < 400);
            entity.setCostMs((int) Math.min(Integer.MAX_VALUE,
                    (System.nanoTime() - startNanos) / 1_000_000));
            entity.setExceptionType(failure == null ? null : failure.getClass().getName());
            apiAccessLogRedisProducer.enqueue(entity);
        } catch (Exception exception) {
            // 访问日志不能影响原始请求响应。
            log.error("接口访问日志写入失败, uri={}, traceId={}", request.getRequestURI(), traceId, exception);
        }
    }

    private static String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Request-ID");
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().replace("-", "")
                : limit(traceId, 64);
    }

    private static String resolveRoute(HttpServletRequest request) {
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return route == null ? limit(request.getRequestURI(), 512) : limit(route.toString(), 512);
    }

    private static String sanitizeQuery(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return null;
        }
        StringBuilder sanitized = new StringBuilder(queryString.length());
        for (String parameter : queryString.split("&", -1)) {
            if (!sanitized.isEmpty()) {
                sanitized.append('&');
            }
            int separatorIndex = parameter.indexOf('=');
            if (separatorIndex < 0) {
                sanitized.append(decodeQueryComponent(parameter));
                continue;
            }

            String key = decodeQueryComponent(parameter.substring(0, separatorIndex));
            String value = decodeQueryComponent(parameter.substring(separatorIndex + 1));
            sanitized.append(key)
                    .append('=')
                    .append(SENSITIVE_QUERY_KEY.matcher(key).matches() ? "[REDACTED]" : value);
        }
        return limit(sanitized.toString(), 2048);
    }

    private static String decodeQueryComponent(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
