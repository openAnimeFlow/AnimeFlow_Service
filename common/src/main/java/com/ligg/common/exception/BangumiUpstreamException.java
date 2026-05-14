package com.ligg.common.exception;

/**
 * 调用 Bangumi Next API 失败（超时、网络等），与 {@link LoginExpiredException}（401）区分。
 */
public class BangumiUpstreamException extends RuntimeException {

    public BangumiUpstreamException(String message) {
        super(message);
    }

    public BangumiUpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
