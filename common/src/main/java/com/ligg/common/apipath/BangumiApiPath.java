/**
 * @author Ligg
 * @date 2026/5/28 01:58
 */
package com.ligg.common.apipath;

public class BangumiApiPath {
    public static final String BANGUMI_API_BASE_URL = "https://api.bgm.tv";
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
     * 条目制作人员列表
     */
    public static final String P1_SUBJECT_STAFF_PERSONS = "/p1/subjects/{subjectId}/staffs/persons";

    /**
     * 条目评论列表
     */
    public static final String P1_SUBJECT_COMMENTS = "/p1/subjects/{subjectId}/comments";

    /**
     * 章节评论列表，{@code {episodeId}} 为 Bangumi episode id
     */
    public static final String P1_EPISODE_COMMENTS = "/p1/episodes/{episodeId}/comments";
}
