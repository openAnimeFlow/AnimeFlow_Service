package com.ligg.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "anime-flow.bangumi")
public class BangumiProperties {

    /** OAuth Client ID */
    private String clientId;

    /** OAuth Client Secret */
    private String clientSecret;

    /** OAuth 回调地址 */
    private String redirectUri;

    /** 请求 User-Agent */
    private String userAgent;

    /** 请求超时时间（秒），默认 30s */
    private int requestTimeoutSeconds = 30;
}
