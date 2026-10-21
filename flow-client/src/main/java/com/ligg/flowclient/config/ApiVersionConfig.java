package com.ligg.flowclient.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 使用 X-Api-Version 请求头选择接口版本，未携带版本号的现有客户端默认使用 v1。 */
@Configuration(proxyBeanMethods = false)
public class ApiVersionConfig implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.useRequestHeader("X-Api-Version")
                .setVersionRequired(false)
                .setDefaultVersion("1")
                .addSupportedVersions("1", "2");
    }
}
