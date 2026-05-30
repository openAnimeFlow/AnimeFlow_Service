package com.ligg.common.apipath;

/**
 * Bangumi 主站（bgm.tv）页面路径。
 */
public final class BgmTvApiPath {

    public static final String BGM_TV_BASE_URL = "https://bgm.tv";

    /**
     * 用户主页，{@code {username}} 为用户名或用户 ID。
     */
    public static final String USER_PAGE = "/user/{username}";

    /**
     * Token 请求地址
     */
    public static final String TOKEN_API = "/oauth/access_token";

    private BgmTvApiPath() {
    }
}
