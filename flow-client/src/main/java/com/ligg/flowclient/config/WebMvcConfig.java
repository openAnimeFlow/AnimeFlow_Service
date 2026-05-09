package com.ligg.flowclient.config;

import com.ligg.flowclient.interceptor.ForumCommentAuthorizationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 拦截器注册
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ForumCommentAuthorizationInterceptor forumCommentAuthorizationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(forumCommentAuthorizationInterceptor)
                .addPathPatterns("/api/forum/comment/**")
                .excludePathPatterns("/api/forum/comment/list");
    }
}
