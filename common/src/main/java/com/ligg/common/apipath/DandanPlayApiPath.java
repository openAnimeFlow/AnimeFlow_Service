/**
 * @author Ligg
 * @date 2026/5/10 15:52
 */
package com.ligg.common.apipath;

public class DandanPlayApiPath {
    /**
     * dandanPlay API
     */
    public static final String DANDAN_PLAY_API_BASE_URL = "https://api.dandanplay.net";

    /**
     * 获取弹幕
     */
    public static final String DANDAN_API_COMMENT = "/api/v2/comment/";

    /**
     * 按关键词搜索作品
     */
    public static final String DANDAN_API_SEARCH_ANIME = "/api/v2/search/anime";

    /**
     * 获取番剧详情
     */
    public static final String DANDAN_API_ELEMENT = "/api/v2/bangumi";

    /**
     * 获取番剧详情(根据bangumiId)
     */
    public static final String DANDAN_API_ELEMENT_BY_BANGUMI_ID = "/api/v2/bangumi/bgmtv/";
}
