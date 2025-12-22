package com.ligg.module.constants;

/**
 * @Author Ligg
 * @Time 2025/8/7
 * <p>
 * bgm 常量
 **/
public class Constants {
    /**
     * Token 请求地址
     */
    public static final String BANGUMI_Token_API = "https://bgm.tv/oauth/access_token";

    /**
     * bangumi 请求头
     */
    public static final String BANGUMI_HEADERS = "Flutter-Anime/1.0.0 (https://github.com/LiggMax/Flutter-Anime.git)";
    /**
     * 授权类型
     */
    public static final String BANGUMI_GRANT_TYPE = "authorization_code";

    public static final String SESSION_KEY = "session";

    //移动端回调
    public static final String MOBILE_CALLBACK_URL = "flow://auth/callback";
}
