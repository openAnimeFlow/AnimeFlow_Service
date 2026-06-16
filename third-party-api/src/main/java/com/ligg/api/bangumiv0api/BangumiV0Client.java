package com.ligg.api.bangumiv0api;

import com.ligg.common.thirdparty.bangumi.enums.SubjectImageType;

/**
 * Bangumi 官方 Open API（api.bgm.tv /v0）。
 */
public interface BangumiV0Client {

    /**
     * 获取条目封面图片 URL（{@code GET /v0/subjects/{subject_id}/image} 302 {@code Location}）。
     */
    String getSubjectImageUrl(int subjectId, SubjectImageType type);
}
