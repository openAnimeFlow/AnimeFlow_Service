package com.ligg.flowclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudflare R2 对象存储配置属性，映射 {@code anime-flow.cloudflare.r2.*}。
 */
@Data
@ConfigurationProperties(prefix = "anime-flow.cloudflare.r2")
public class CloudflareR2Properties {

    /**
     * R2 S3  API 端点
     */
    private String endpoint;

    /**
     * R2 API 令牌的 Access Key ID
     */
    private String accessKey;

    /**
     * R2 API 令牌的 Secret Access Key
     */
    private String secretKey;

    /**
     * R2 存储桶名称
     */
    private String bucket;

    /**
     * 公开访问域名，
     * 用于拼接上传后的文件公开 URL。
     */
    private String publicUrl;
}
