package com.ligg.common.storage;

import java.io.InputStream;

/**
 * 对象存储抽象接口，屏蔽底层实现（S3 / R2 / MinIO 等）。
 */
public interface ObjectStorageService {

    /**
     * 上传文件到对象存储。
     *
     * @param key           对象键（路径），例如 {@code avatars/123/uuid.webp}
     * @param inputStream   文件输入流
     * @param contentLength 文件字节数
     * @param contentType   MIME 类型，例如 {@code image/webp}
     * @return 文件的公开访问 URL
     */
    String upload(String key, InputStream inputStream, long contentLength, String contentType);

    /**
     * 删除对象存储中的文件。
     *
     * @param key 对象键
     */
    void delete(String key);

    /**
     * 获取对象的公开访问 URL。
     *
     * @param key 对象键
     * @return 公开访问 URL
     */
    String getPublicUrl(String key);
}
