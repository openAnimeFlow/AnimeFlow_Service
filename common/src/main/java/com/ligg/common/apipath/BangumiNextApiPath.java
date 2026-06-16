/**
 * @author Ligg
 * @date 2026/5/28 01:58
 */
package com.ligg.common.apipath;

public class BangumiNextApiPath {

    private BangumiNextApiPath() {}
    public static final String BANGUMI_NEXT_API_BASE_URL = "https://next.bgm.tv";

    /**
     * 获取每日放送
     */
    public static final String P1_CALENDAR = "/p1/calendar";

    /**
     * 趋势条目（如 type=2 为动画）
     */
    public static final String P1_TRENDING_SUBJECTS = "/p1/trending/subjects";

    /**
     * 条目
     */
    public static final String P1_SUBJECTS = "/p1/subjects";

    /**
     * 条目搜索
     */
    public static final String P1_SEARCH_SUBJECTS = "/p1/search/subjects";

    /**
     * 条目章节列表
     */
    public static final String P1_SUBJECT_EPISODES = "/p1/subjects/{subjectId}/episodes";

    /**
     * 条目角色列表
     */
    public static final String P1_SUBJECT_CHARACTERS = "/p1/subjects/{subjectId}/characters";

    /**
     * 角色详情
     */
    public static final String P1_CHARACTER = "/p1/characters/{characterId}";

    /**
     * 角色吐槽列表
     */
    public static final String P1_CHARACTER_COMMENTS = "/p1/characters/{characterId}/comments";

    /**
     * 角色出演作品列表
     */
    public static final String P1_CHARACTER_CASTS = "/p1/characters/{characterId}/casts";

    /**
     * 条目制作人员列表
     */
    public static final String P1_SUBJECT_STAFF_PERSONS = "/p1/subjects/{subjectId}/staffs/persons";

    /**
     * 条目评论列表
     */
    public static final String P1_SUBJECT_COMMENTS = "/p1/subjects/{subjectId}/comments";

    /**
     * 条目关联列表
     */
    public static final String P1_SUBJECT_RELATIONS = "/p1/subjects/{subjectId}/relations";

    /**
     * 章节评论列表，{@code {episodeId}} 为 Bangumi episode id
     */
    public static final String P1_EPISODE_COMMENTS = "/p1/episodes/{episodeId}/comments";

    /**
     * 用户资料，{@code {username}} 为 Bangumi 用户名
     */
    public static final String P1_USER = "/p1/users/{username}";

    /**
     * 用户条目收藏，{@code {username}} 为 Bangumi 用户名或用户 ID
     */
    public static final String P1_USER_COLLECTION_SUBJECTS = "/p1/users/{username}/collections/subjects";

    /**
     * 条目收藏
     */
    public static final String P1_COLLECTION_SUBJECTS = "/p1/collections/subjects";
}
