package com.ligg.flow_client.interceptor;

import com.ligg.common.exception.MissingAuthorizationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 论坛评论接口：读取 Authorization 请求头，解析 access_token 并放入 request attribute。
 */
@Component
public class ForumCommentAuthorizationInterceptor implements HandlerInterceptor {

    /**
     * {@link HttpServletRequest#getAttribute(String)} 键，值为请求头 Authorization 的原始字符串。
     * 须为编译期常量字符串，以便在 {@code @RequestAttribute} 等注解中使用。
     */
    public static final String AUTHORIZATION_REQUEST_ATTRIBUTE = "com.ligg.forum.comment.authorization";

    /**
     * 解析后的 access_token（已去掉 {@code Bearer } 前缀），供调用 Bangumi 等接口使用。
     */
    public static final String ACCESS_TOKEN_REQUEST_ATTRIBUTE = "com.ligg.forum.comment.accessToken";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)) {
            throw new MissingAuthorizationException("缺少 Authorization 请求头");
        }

        String accessToken = parseBearerAccessToken(authorization);
        if (!StringUtils.hasText(accessToken)) {
            throw new MissingAuthorizationException("缺少或无效的访问令牌");
        }

        request.setAttribute(AUTHORIZATION_REQUEST_ATTRIBUTE, authorization);
        request.setAttribute(ACCESS_TOKEN_REQUEST_ATTRIBUTE, accessToken);
        return true;
    }

    /**
     * 从 Authorization 取值中解析 access_token（不含 {@code Bearer } 前缀）。
     */
    private static String parseBearerAccessToken(String authorization) {
        String v = authorization.trim();
        if (v.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return v.substring(7).trim();
        }
        return v;
    }
}
