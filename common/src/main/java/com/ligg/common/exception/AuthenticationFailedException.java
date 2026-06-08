package com.ligg.common.exception;

/**
 * 邮箱或密码校验失败时抛出
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        super("邮箱或密码错误");
    }

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
