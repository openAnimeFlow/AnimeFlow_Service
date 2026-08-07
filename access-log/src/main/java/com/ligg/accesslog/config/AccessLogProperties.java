package com.ligg.accesslog.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 接口访问日志配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "anime-flow.access-log")
public class AccessLogProperties {

    /** 是否启用访问日志。 */
    private boolean enabled = true;
}
