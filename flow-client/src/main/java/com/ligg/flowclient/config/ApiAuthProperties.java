package com.ligg.flowclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端 API 签名鉴权参数
 */
@Data
@ConfigurationProperties(prefix = "anime-flow.api-auth")
public class ApiAuthProperties {

    private boolean enabled = true;

    private String appId;

    private String secret;

    /**
     * 允许的时间戳偏差（秒），防止重放攻击。
     */
    private int timestampSkewSeconds = 300;

    private List<String> pathPatterns = new ArrayList<>(List.of("/api/**"));

    private List<String> excludePathPatterns = new ArrayList<>(List.of(
            "/api/oauth/callback",
            "/api/no_session",
            "/api/oauth-success",
            "/api/success"
    ));
}
