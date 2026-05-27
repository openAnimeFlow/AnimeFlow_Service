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
     * 条目详情，{@code {subjectId}} 为 Bangumi subject id
     */
    public static final String P1_SUBJECT = "/p1/subjects/{subjectId}";

    /**
     * 条目章节列表
     */
    public static final String P1_SUBJECT_EPISODES = "/p1/subjects/{subjectId}/episodes";
}
