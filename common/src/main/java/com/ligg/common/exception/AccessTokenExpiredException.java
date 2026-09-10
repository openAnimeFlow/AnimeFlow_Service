package com.ligg.common.exception;

public class AccessTokenExpiredException extends LoginExpiredException {
    public AccessTokenExpiredException() {
        super();
    }

    public AccessTokenExpiredException(Throwable cause) { super(cause); }
}
