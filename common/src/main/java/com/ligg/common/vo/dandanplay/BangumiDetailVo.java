package com.ligg.common.vo.dandanplay;

public record BangumiDetailVo(
        DanmakuDetailVo bangumi,
        int errorCode,
        boolean success,
        String errorMessage) {
}
