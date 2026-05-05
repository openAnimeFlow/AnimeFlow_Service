package com.ligg.common.exception;

/**
 * 调用 Bangumi 接口返回 401 时抛出，表示访问令牌失效或登录已过期。
 */
public class BangumiLoginExpiredException extends RuntimeException {

    public BangumiLoginExpiredException(Throwable cause) {
        super("Bangumi 登录已过期或令牌无效", cause);
    }
}
