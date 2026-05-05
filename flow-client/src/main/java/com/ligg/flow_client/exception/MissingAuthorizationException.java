package com.ligg.flow_client.exception;

/**
 * 论坛评论等接口要求携带 Authorization 请求头但未提供或为空时抛出。
 */
public class MissingAuthorizationException extends RuntimeException {

    public MissingAuthorizationException(String message) {
        super(message);
    }
}
