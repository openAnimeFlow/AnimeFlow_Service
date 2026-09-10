package com.ligg.common.exception;

public class RefreshTokenInvalidException extends AuthenticationFailedException {
    public RefreshTokenInvalidException() {
        super("刷新令牌无效或已过期");
    }
}
