package com.ligg.flowclient.config;

import com.ligg.common.thirdparty.bangumi.enums.SubjectBrowseSort;
import com.ligg.common.thirdparty.bangumi.enums.SubjectSearchSort;
import com.ligg.flowclient.interceptor.ApiSignatureInterceptor;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.interceptor.IpRateLimitInterceptor;
import com.ligg.flowclient.interceptor.OptionalAuthorizationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 拦截器注册
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({RateLimitProperties.class, ApiAuthProperties.class, JwtProperties.class})
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiSignatureInterceptor apiSignatureInterceptor;
    private final AuthorizationInterceptor authorizationInterceptor;
    private final OptionalAuthorizationInterceptor optionalAuthorizationInterceptor;
    private final IpRateLimitInterceptor ipRateLimitInterceptor;
    private final RateLimitProperties rateLimitProperties;
    private final ApiAuthProperties apiAuthProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiSignatureInterceptor)
                .addPathPatterns(apiAuthProperties.getPathPatterns())
                .excludePathPatterns(apiAuthProperties.getExcludePathPatterns())
                .order(0);

        registry.addInterceptor(ipRateLimitInterceptor)
                .addPathPatterns(rateLimitProperties.getPathPatterns())
                .excludePathPatterns(rateLimitProperties.getExcludePathPatterns())
                .order(1);

        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns(
                        "/api/forum/comment/**",
                        "/api/v1/danmaku",
                        "/api/v1/users/**",
                        "/api/v1/account/oauth/**"
                )
                .excludePathPatterns("/api/forum/comment/list")
                .order(2);

        registry.addInterceptor(optionalAuthorizationInterceptor)
                .addPathPatterns("/api/v1/bangumi/subjects/**", "/api/v1/bangumi/search/subjects")
                .order(3);
    }

    /**
     * 注册 query 枚举转换器，支持传 "rank"、"match" 等小写值。
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, SubjectBrowseSort.class, SubjectBrowseSort::fromValue);
        registry.addConverter(String.class, SubjectSearchSort.class, SubjectSearchSort::fromValue);
    }
}
