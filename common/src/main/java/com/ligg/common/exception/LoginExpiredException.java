package com.ligg.common.exception;

public class LoginExpiredException extends RuntimeException {

    public LoginExpiredException() {
        super("登录已过期或令牌无效");
    }

    public LoginExpiredException(Throwable cause) {
        super("登录已过期或令牌无效", cause);
    }
}
