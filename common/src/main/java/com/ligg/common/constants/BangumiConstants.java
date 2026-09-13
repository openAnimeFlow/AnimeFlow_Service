/**
 * @author Ligg
 * @date 2026/5/28 17:53
 */
package com.ligg.common.constants;

public class BangumiConstants {

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
     * Bangumi 季番周表缓存键前缀
     */
    public static final String BANGUMI_SEASON_CALENDAR_CACHE_KEY_PREFIX = "bangumi:season-calendar";

    /**
     * Bangumi 季番周表缓存时长（秒）
     */
    public static final long BANGUMI_SEASON_CALENDAR_CACHE_TTL_SECONDS = 43200;

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
    public static final long BANGUMI_TRENDING_CACHE_TTL_SECONDS = 86400;

    /**
     * 条目浏览列表每页条数
     */
    public static final int BANGUMI_SUBJECTS_PAGE_SIZE = 24;

    /**
     * 条目浏览列表允许访问的最大页码
     */
    public static final int BANGUMI_SUBJECTS_MAX_PAGE = 100;

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
    public static final long BANGUMI_SUBJECT_COMMENTS_CACHE_TTL_SECONDS = 3600;

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
}
