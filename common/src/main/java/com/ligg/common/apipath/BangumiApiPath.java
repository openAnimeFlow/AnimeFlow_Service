package com.ligg.common.apipath;

/**
 * Bangumi 官方 Open API（api.bgm.tv /v0）。
 */
public final class BangumiApiPath {

    public static final String BANGUMI_API_BASE_URL = "https://api.bgm.tv";

    /**
     * 条目封面 302 重定向，{@code {subjectId}} 为 Bangumi 条目 ID。
     */
    public static final String V0_SUBJECT_IMAGE = "/v0/subjects/{subjectId}/image";

    /**
     * 无封面时 Bangumi 返回的默认图。
     */
    public static final String DEFAULT_SUBJECT_IMAGE_URL = "https://lain.bgm.tv/img/no_icon_subject.png";

    private BangumiApiPath() {
    }
}
