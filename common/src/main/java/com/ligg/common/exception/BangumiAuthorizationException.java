package com.ligg.common.exception;

public class BangumiAuthorizationException extends LoginExpiredException {
    public BangumiAuthorizationException() {
        super();
    }

    public BangumiAuthorizationException(Throwable cause) { super(cause); }
}
