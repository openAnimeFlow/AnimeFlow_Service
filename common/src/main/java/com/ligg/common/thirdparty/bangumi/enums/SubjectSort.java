package com.ligg.common.thirdparty.bangumi.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Bangumi 条目搜索排序 {@code SubjectSearchSort}。
 */
@Getter
@RequiredArgsConstructor
public enum SubjectSort {

    MATCH("match"),
    HEAT("heat"),
    RANK("rank"),
    SCORE("score");

    @JsonValue
    private final String value;

    @JsonCreator
    public static SubjectSort fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (SubjectSort sort : values()) {
            if (sort.value.equalsIgnoreCase(value)) {
                return sort;
            }
        }
        throw new IllegalArgumentException("Unknown subject search sort: " + value);
    }
}
