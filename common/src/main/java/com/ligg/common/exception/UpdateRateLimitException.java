package com.ligg.common.exception;

/**
 * 更新操作频率超过限制时抛出
 */
public class UpdateRateLimitException extends RuntimeException {

    public UpdateRateLimitException(String message) {
        super(message);
    }
}
