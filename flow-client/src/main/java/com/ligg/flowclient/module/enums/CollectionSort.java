package com.ligg.flowclient.module.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 当前用户收藏列表排序。 */
@Getter
@RequiredArgsConstructor
public enum CollectionSort {
    /** 收藏更新时间倒序。 */
    DEFAULT("default"),
    /** user_play_history.last_played_at 倒序。 */
    WATCH("watch");

    private final String value;

    public static CollectionSort fromValue(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        for (CollectionSort sort : values()) {
            if (sort.value.equalsIgnoreCase(value)) {
                return sort;
            }
        }
        throw new IllegalArgumentException("Unknown collection sort: " + value);
    }
}
