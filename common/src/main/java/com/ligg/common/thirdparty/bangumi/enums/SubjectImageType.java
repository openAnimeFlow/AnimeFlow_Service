package com.ligg.common.thirdparty.bangumi.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Bangumi 条目图片规格，用于 {@code GET /v0/subjects/{subject_id}/image?type=}。
 */
@Getter
@RequiredArgsConstructor
public enum SubjectImageType {

    SMALL("small"),
    GRID("grid"),
    LARGE("large"),
    MEDIUM("medium"),
    COMMON("common");

    @JsonValue
    private final String value;

    @JsonCreator
    public static SubjectImageType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (SubjectImageType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown subject image type: " + value);
    }
}
