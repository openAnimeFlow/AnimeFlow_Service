package com.ligg.flowclient.config;

import com.ligg.flowclient.interceptor.ForumCommentAuthorizationInterceptor;
import com.ligg.flowclient.interceptor.IpRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 拦截器注册
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ForumCommentAuthorizationInterceptor forumCommentAuthorizationInterceptor;
    private final IpRateLimitInterceptor ipRateLimitInterceptor;
    private final RateLimitProperties rateLimitProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ipRateLimitInterceptor)
                .addPathPatterns(rateLimitProperties.getPathPatterns())
                .excludePathPatterns(rateLimitProperties.getExcludePathPatterns())
                .order(0);

        registry.addInterceptor(forumCommentAuthorizationInterceptor)
                .addPathPatterns("/api/forum/comment/**")
                .excludePathPatterns("/api/forum/comment/list")
                .order(1);
    }
}
