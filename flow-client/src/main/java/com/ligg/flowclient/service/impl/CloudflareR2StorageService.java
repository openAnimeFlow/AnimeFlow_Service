package com.ligg.flowclient.service.impl;

import com.ligg.common.storage.ObjectStorageService;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

/**
 * 基于 Cloudflare R2（S3 兼容 API）的对象存储实现。
 */
@Slf4j
public class CloudflareR2StorageService implements ObjectStorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final String publicUrl;

    public CloudflareR2StorageService(S3Client s3Client, String bucket, String publicUrl) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.publicUrl = publicUrl.endsWith("/")
                ? publicUrl.substring(0, publicUrl.length() - 1)
                : publicUrl;
    }

    @Override
    public String upload(String key, InputStream inputStream, long contentLength, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
        log.info("文件上传成功: bucket={}, key={}", bucket, key);
        return getPublicUrl(key);
    }

    @Override
    public void delete(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        s3Client.deleteObject(request);
        log.info("文件删除成功: bucket={}, key={}", bucket, key);
    }

    @Override
    public String getPublicUrl(String key) {
        return publicUrl + "/" + key;
    }
}
