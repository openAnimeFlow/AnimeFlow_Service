package com.ligg.common.exception;

/**
 * 论坛评论等接口要求携带 Authorization 请求头但未提供或为空时抛出。
 */
public class AuthorizationException extends RuntimeException {

    public AuthorizationException(String message) {
        super(message);
    }
}
