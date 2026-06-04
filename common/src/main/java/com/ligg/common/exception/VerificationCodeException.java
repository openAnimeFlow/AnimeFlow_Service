package com.ligg.common.exception;

/**
 * 验证码校验失败（如邮箱验证码与 Redis 中不一致）。
 */
public class VerificationCodeException extends RuntimeException {

    public VerificationCodeException(String message) {
        super(message);
    }
}
