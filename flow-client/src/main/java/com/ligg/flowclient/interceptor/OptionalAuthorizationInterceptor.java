package com.ligg.flowclient.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 可选登录：读取 Authorization 并解析 Bearer token 写入 request attribute，无请求头时不拦截。
 */
@Component
public class OptionalAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AuthorizationInterceptor.attachAccessTokenIfPresent(request);
        return true;
    }
}
