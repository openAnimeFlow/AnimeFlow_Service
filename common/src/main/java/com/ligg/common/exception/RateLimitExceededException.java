package com.ligg.common.exception;

/**
 * 接口限流触发时抛出
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException() {
        super();
    }

    public RateLimitExceededException(String message) {
        super(message);
    }
}
