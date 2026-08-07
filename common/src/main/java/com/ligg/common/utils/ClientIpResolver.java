package com.ligg.common.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * Cloudflare/反向代理场景下的客户端 IP 解析器。
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String ip = request.getHeader("CF-Connecting-IP");
        if (!StringUtils.hasText(ip)) {
            ip = firstIp(request.getHeader("X-Forwarded-For"));
        }
        if (!StringUtils.hasText(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(ip)) {
            ip = request.getRemoteAddr();
        }
        return StringUtils.hasText(ip) ? ip.trim() : "unknown";
    }

    private static String firstIp(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.split(",", 2)[0].trim();
    }
}
