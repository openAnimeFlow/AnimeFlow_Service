package com.ligg.flowclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 接口限流参数
 */
@Data
@ConfigurationProperties(prefix = "anime-flow.rate-limit")
public class RateLimitProperties {

    private  boolean enabled = true;

    /**
     * 单个时间窗口内允许的最大请求次数。
     */
    private int maxRequests = 30;

    /**
     * 时间窗口长度（秒）。
     */
    private int windowSeconds = 60;

    /**
     * Redis 键前缀，完整键为 prefix + clientIp。
     */
    private String keyPrefix = "flow:rl:ip:";

    private List<String> pathPatterns = new ArrayList<>(List.of("/api/**"));

    private List<String> excludePathPatterns = new ArrayList<>();

}
