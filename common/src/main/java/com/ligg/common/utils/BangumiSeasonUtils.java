package com.ligg.common.utils;

import java.time.LocalDate;

/**
 * Bangumi 季度月份计算工具。季度起始月固定为 1、4、7、10。
 */
public final class BangumiSeasonUtils {

    private BangumiSeasonUtils() {
    }

    public static int startMonthOf(int month) {
        return switch (month) {
            case 1, 2, 3 -> 1;
            case 4, 5, 6 -> 4;
            case 7, 8, 9 -> 7;
            default -> 10;
        };
    }

    /**
     * 返回 {@code yyyy-MM} 形式的当前季度起始月份。
     */
    public static String currentSeasonMonthPrefix(LocalDate date) {
        return String.format("%04d-%02d", date.getYear(), startMonthOf(date.getMonthValue()));
    }
}
