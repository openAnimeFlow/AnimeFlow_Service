package com.ligg.common.thirdparty.bangumi.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Bangumi 条目搜索排序 {@code SubjectSearchSort}，用于 {@code POST /p1/search/subjects}。
 */
@Getter
@RequiredArgsConstructor
public enum SubjectSearchSort {

    MATCH("match"),
    HEAT("heat"),
    RANK("rank"),
    SCORE("score");

    @JsonValue
    private final String value;

    @JsonCreator
    public static SubjectSearchSort fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (SubjectSearchSort sort : values()) {
            if (sort.value.equalsIgnoreCase(value)) {
                return sort;
            }
        }
        throw new IllegalArgumentException("Unknown subject search sort: " + value);
    }
}
