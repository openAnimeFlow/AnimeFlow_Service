package com.ligg.common.thirdparty.bangumi.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Bangumi 条目浏览排序 {@code SubjectBrowseSort}，用于 {@code GET /p1/subjects}。
 */
@Getter
@RequiredArgsConstructor
public enum SubjectBrowseSort {

    // 按评分排序
    RANK("rank"),
    TRENDS("trends"),
    // 按收藏数排序
    COLLECTS("collects"),
    // 按日期排序
    DATE("date"),
    TITLE("title");

    @JsonValue
    private final String value;

    @JsonCreator
    public static SubjectBrowseSort fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (SubjectBrowseSort sort : values()) {
            if (sort.value.equalsIgnoreCase(value)) {
                return sort;
            }
        }
        throw new IllegalArgumentException("Unknown subject browse sort: " + value);
    }
}
