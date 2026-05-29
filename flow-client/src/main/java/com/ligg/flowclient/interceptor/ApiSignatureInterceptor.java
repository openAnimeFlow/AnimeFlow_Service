package com.ligg.flowclient.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.flowclient.config.ApiAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * API 签名鉴权：校验 X-Auth / X-AppId / X-Timestamp / X-Signature 请求头。
 * <p>
 * 签名算法与客户端一致：Base64(SHA256(appId + timestamp + path + secret))
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiSignatureInterceptor implements HandlerInterceptor {

    private static final String HEADER_X_AUTH = "X-Auth";
    private static final String HEADER_X_APP_ID = "X-AppId";
    private static final String HEADER_X_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_X_SIGNATURE = "X-Signature";

    private final ApiAuthProperties apiAuthProperties;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!apiAuthProperties.isEnabled()) {
            return true;
        }

        String xAuth = request.getHeader(HEADER_X_AUTH);
        String appId = request.getHeader(HEADER_X_APP_ID);
        String timestampHeader = request.getHeader(HEADER_X_TIMESTAMP);
        String signature = request.getHeader(HEADER_X_SIGNATURE);

        if (!"1".equals(xAuth)
                || !StringUtils.hasText(appId)
                || !StringUtils.hasText(timestampHeader)
                || !StringUtils.hasText(signature)
                || !appId.equals(apiAuthProperties.getAppId())) {
            return unauthorized(response);
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            return unauthorized(response);
        }

        long nowSeconds = System.currentTimeMillis() / 1000;
        long skew = apiAuthProperties.getTimestampSkewSeconds();
        if (Math.abs(nowSeconds - timestamp) > skew) {
            return unauthorized(response);
        }

        String path = request.getRequestURI();
        String expectedSignature = generateSignature(appId, timestamp, path, apiAuthProperties.getSecret());
        if (!constantTimeEquals(expectedSignature, signature.trim())) {
            log.warn("API 签名校验失败: path={}, appId={}", path, appId);
            return unauthorized(response);
        }

        return true;
    }

    /**
     * 生成 API 签名，供服务端校验或客户端参考实现。
     */
    public static String generateSignature(String appId, long timestamp, String path, String secret) {
        String data = appId + timestamp + path + secret;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("生成 API 签名失败", e);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private boolean unauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Result<Void> body = Result.error(ResponseCode.UNAUTHORIZED);
        response.getWriter().write(objectMapper.writeValueAsString(body));
        return false;
    }
}
