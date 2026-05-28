/**
 * @author Ligg
 * @date 2026/5/28 17:53
 */
package com.ligg.common.constants.bangumi;

public class BangumiConstants {
    /**
     * Bangumi 每日放送缓存键
     */
    public static final String BANGUMI_CALENDAR_CACHE_KEY = "bangumi:calendar";

    /**
     * Bangumi 每日放送重建缓存锁
     */
    public static final String BANGUMI_CALENDAR_LOCK_KEY = "bangumi:calendar:lock";

    /**
     * Bangumi 每日放送缓存时长（秒）
     */
    public static final long BANGUMI_CALENDAR_CACHE_TTL_SECONDS = 3600;

    /**
     * Bangumi 每日放送重建缓存锁时长（秒）
     */
    public static final long BANGUMI_CALENDAR_LOCK_TTL_SECONDS = 60;

    /**
     * 缓存击穿时，等待其他请求重建缓存的最长时间（毫秒）
     */
    public static final long BANGUMI_CALENDAR_CACHE_WAIT_MILLIS = 35_000;

    /**
     * 等待缓存重建时的轮询间隔（毫秒）
     */
    public static final long BANGUMI_CALENDAR_CACHE_POLL_INTERVAL_MILLIS = 100;
}
