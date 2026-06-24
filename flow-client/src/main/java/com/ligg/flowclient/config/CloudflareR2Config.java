package com.ligg.flowclient.config;

import com.ligg.common.storage.ObjectStorageService;
import com.ligg.flowclient.service.impl.CloudflareR2StorageService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Cloudflare R2 对象存储配置，创建 S3Client 和 ObjectStorageService Bean。
 */
@Configuration
@EnableConfigurationProperties(CloudflareR2Properties.class)
public class CloudflareR2Config {

    @Bean
    public S3Client s3Client(CloudflareR2Properties properties) {
        URI endpoint = URI.create(properties.getEndpoint());
        if (endpoint.getPath() != null && !endpoint.getPath().isEmpty() && !endpoint.getPath().equals("/")) {
            endpoint = URI.create(endpoint.getScheme() + "://" + endpoint.getHost());
        }

        return S3Client.builder()
                .endpointOverride(endpoint)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                .region(Region.of("auto"))
                .forcePathStyle(true)
                .build();
    }

    @Bean
    public ObjectStorageService objectStorageService(S3Client s3Client, CloudflareR2Properties properties) {
        return new CloudflareR2StorageService(s3Client, properties.getBucket(), properties.getPublicUrl());
    }
}
