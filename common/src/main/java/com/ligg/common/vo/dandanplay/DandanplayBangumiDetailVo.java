package com.ligg.common.vo.dandanplay;

public record DandanplayBangumiDetailVo(
        DandanplayDetailVo bangumi,
        int errorCode,
        boolean success,
        String errorMessage) {
}
