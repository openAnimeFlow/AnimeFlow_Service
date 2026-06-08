package com.ligg.flowclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AnimeFlow JWT 签发参数。
 */
@Data
@ConfigurationProperties(prefix = "anime-flow.jwt")
public class JwtProperties {

    /**
     * HS256 签名密钥，长度至少 32 字符。
     */
    private String secret;

    /**
     * access_token 有效期（秒），默认 2 小时。
     */
    private long expireSeconds = 7200L;

    /**
     * refresh_token 有效期（秒），默认 7 天。
     */
    private long refreshExpireSeconds = 604800L;
}
