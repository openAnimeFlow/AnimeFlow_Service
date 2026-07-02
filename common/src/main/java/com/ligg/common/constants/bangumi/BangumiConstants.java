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
    public static final long BANGUMI_CALENDAR_CACHE_TTL_SECONDS = 17280;

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

    /**
     * Bangumi 趋势条目缓存键前缀
     */
    public static final String BANGUMI_TRENDING_CACHE_KEY_PREFIX = "bangumi:trending";

    /**
     * Bangumi 趋势条目最多缓存页数
     */
    public static final int BANGUMI_TRENDING_MAX_CACHE_PAGE = 10;

    /**
     * Bangumi 趋势条目缓存时长（秒）
     */
    public static final long BANGUMI_TRENDING_CACHE_TTL_SECONDS = 900;

    /**
     * Bangumi 条目列表缓存键前缀
     */
    public static final String BANGUMI_SUBJECTS_CACHE_KEY_PREFIX = "bangumi:subjects";

    /**
     * Bangumi 条目列表最多缓存页数
     */
    public static final int BANGUMI_SUBJECTS_MAX_CACHE_PAGE = 10;

    /**
     * Bangumi 条目列表缓存时长（秒）
     */
    public static final long BANGUMI_SUBJECTS_CACHE_TTL_SECONDS = 1800;

    /**
     * Bangumi 条目详情缓存键前缀
     */
    public static final String BANGUMI_SUBJECT_DETAIL_CACHE_KEY_PREFIX = "bangumi:subject:detail";

    /**
     * Bangumi 条目详情缓存时长（秒）
     */
    public static final long BANGUMI_SUBJECT_DETAIL_CACHE_TTL_SECONDS = 60;

    /**
     * Bangumi 条目角色列表缓存键前缀
     */
    public static final String BANGUMI_SUBJECT_CHARACTERS_CACHE_KEY_PREFIX = "bangumi:subject:characters";

    /**
     * Bangumi 条目角色列表最多缓存页数
     */
    public static final int BANGUMI_SUBJECT_CHARACTERS_MAX_CACHE_PAGE = 10;

    /**
     * Bangumi 条目角色列表缓存时长（秒）
     */
    public static final long BANGUMI_SUBJECT_CHARACTERS_CACHE_TTL_SECONDS = 1800;

    /**
     * Bangumi 条目制作人员列表缓存键前缀
     */
    public static final String BANGUMI_SUBJECT_STAFF_PERSONS_CACHE_KEY_PREFIX = "bangumi:subject:staff:persons";

    /**
     * Bangumi 条目制作人员列表最多缓存页数
     */
    public static final int BANGUMI_SUBJECT_STAFF_PERSONS_MAX_CACHE_PAGE = 10;

    /**
     * Bangumi 条目制作人员列表缓存时长（秒）
     */
    public static final long BANGUMI_SUBJECT_STAFF_PERSONS_CACHE_TTL_SECONDS = 1800;

    /**
     * Bangumi 条目评论列表缓存键前缀
     */
    public static final String BANGUMI_SUBJECT_COMMENTS_CACHE_KEY_PREFIX = "bangumi:subject:comments";

    /**
     * Bangumi 条目评论列表最多缓存页数
     */
    public static final int BANGUMI_SUBJECT_COMMENTS_MAX_CACHE_PAGE = 5;

    /**
     * Bangumi 条目评论列表缓存时长（秒）
     */
    public static final long BANGUMI_SUBJECT_COMMENTS_CACHE_TTL_SECONDS = 900;

    /**
     * Bangumi 条目关联列表缓存键前缀
     */
    public static final String BANGUMI_SUBJECT_RELATIONS_CACHE_KEY_PREFIX = "bangumi:subject:relations";

    /**
     * Bangumi 条目关联列表最多缓存页数
     */
    public static final int BANGUMI_SUBJECT_RELATIONS_MAX_CACHE_PAGE = 1;

    /**
     * Bangumi 条目关联列表缓存时长（秒）
     */
    public static final long BANGUMI_SUBJECT_RELATIONS_CACHE_TTL_SECONDS = 60;

    /**
     * Bangumi 角色详情缓存键前缀
     */
    public static final String BANGUMI_CHARACTER_DETAIL_CACHE_KEY_PREFIX = "bangumi:character:detail";

    /**
     * Bangumi 角色详情缓存时长（秒）
     */
    public static final long BANGUMI_CHARACTER_DETAIL_CACHE_TTL_SECONDS = 120;

    /**
     * Bangumi 角色出演作品列表缓存键前缀
     */
    public static final String BANGUMI_CHARACTER_CASTS_CACHE_KEY_PREFIX = "bangumi:character:casts";

    /**
     * Bangumi 角色出演作品列表最多缓存页数
     */
    public static final int BANGUMI_CHARACTER_CASTS_MAX_CACHE_PAGE = 2;

    /**
     * Bangumi 角色出演作品列表缓存时长（秒）
     */
    public static final long BANGUMI_CHARACTER_CASTS_CACHE_TTL_SECONDS = 120;

    /**
     * Bangumi 角色吐槽列表缓存键前缀
     */
    public static final String BANGUMI_CHARACTER_COMMENTS_CACHE_KEY_PREFIX = "bangumi:character:comments";

    /**
     * Bangumi 角色吐槽列表最多缓存页数
     */
    public static final int BANGUMI_CHARACTER_COMMENTS_MAX_CACHE_PAGE = 2;

    /**
     * Bangumi 角色吐槽列表缓存时长（秒）
     */
    public static final long BANGUMI_CHARACTER_COMMENTS_CACHE_TTL_SECONDS = 60;

    /**
     * Bangumi 用户资料缓存键前缀
     */
    public static final String BANGUMI_USER_PROFILE_CACHE_KEY_PREFIX = "bangumi:user:profile";

    /**
     * Bangumi 用户资料缓存时长（秒）
     */
    public static final long BANGUMI_USER_PROFILE_CACHE_TTL_SECONDS = 300;

    /**
     * Bangumi 用户主页统计缓存键前缀（bgm.tv HTML 解析）
     */
    public static final String BANGUMI_USER_STATISTICS_CACHE_KEY_PREFIX = "bangumi:user:statistics";

    /**
     * Bangumi 用户主页统计缓存时长（秒）
     */
    public static final long BANGUMI_USER_STATISTICS_CACHE_TTL_SECONDS = 300;

    /**
     * Bangumi 用户条目收藏缓存键前缀
     */
    public static final String BANGUMI_USER_COLLECTIONS_CACHE_KEY_PREFIX = "bangumi:user:collections";

    /**
     * Bangumi 用户条目收藏最多缓存页数
     */
    public static final int BANGUMI_USER_COLLECTIONS_MAX_CACHE_PAGE = 5;

    /**
     * Bangumi 用户条目收藏缓存时长（秒）
     */
    public static final long BANGUMI_USER_COLLECTIONS_CACHE_TTL_SECONDS = 120;

    /**
     * Bangumi 条目搜索串行执行锁
     */
    public static final String BANGUMI_SEARCH_LOCK_KEY = "bangumi:search:lock";

    /**
     * Bangumi 条目搜索请求间隔冷却键（两次搜索之间至少间隔 1.5 秒）
     */
    public static final String BANGUMI_SEARCH_COOLDOWN_KEY = "bangumi:search:cooldown";

    /**
     * 两次搜索请求之间的最小间隔（毫秒）
     */
    public static final long BANGUMI_SEARCH_COOLDOWN_MILLIS = 1500;

    /**
     * 条目搜索串行锁时长（秒）
     */
    public static final long BANGUMI_SEARCH_LOCK_TTL_SECONDS = 60;

    /**
     * 条目搜索排队等待最长时间（毫秒）
     */
    public static final long BANGUMI_SEARCH_WAIT_MILLIS = 35_000;

    /**
     * 条目搜索排队轮询间隔（毫秒）
     */
    public static final long BANGUMI_SEARCH_POLL_INTERVAL_MILLIS = 100;
}
