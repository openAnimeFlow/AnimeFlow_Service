package com.ligg.common.constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @Author Ligg
 * @Time 2025/8/7
 * <p>
 **/
public class Constants {

    /// 随机UA列表
    public static final List<String> userAgentList = new ArrayList<>(
            Arrays.asList(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.1",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.1 Safari/605.1.15",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 Edg/134.0.0.0",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36 Edg/136.0.0.0"
            )
    );

    /**
     * 授权类型
     */
    public static final String BANGUMI_GRANT_TYPE = "authorization_code";

    public static final String SESSION_KEY = "session";

    //移动端回调
    public static final String MOBILE_CALLBACK_URL = "flow://auth/callback";

    public static final String AUTO_TOKEN_KEY = "auto_token";

    /**
     * 邮箱验证码 Redis 键前缀，完整键为 email:verification:{email}
     */
    public static final String EMAIL_VERIFICATION_KEY = "email:verification";

    /**
     * 邮件发送成功后的冷却 Redis 键前缀，完整键为 animeflow:email:send:cooldown:{email}
     */
    public static final String EMAIL_SEND_COOLDOWN_KEY = "animeflow:email:send:cooldown";

    /**
     * AnimeFlow access_token Redis 键前缀，完整键为 animeflow:auth:token:{accessJti}
     */
    public static final String AUTH_TOKEN_KEY = "animeflow:auth:token";

    /**
     * AnimeFlow refresh_token Redis 键前缀，完整键为 animeflow:auth:refresh:{refreshJti}
     */
    public static final String AUTH_REFRESH_TOKEN_KEY = "animeflow:auth:refresh";

    /**
     * AnimeFlow 登录会话 Redis 键前缀，完整键为 animeflow:auth:session:{sessionId}
     */
    public static final String AUTH_SESSION_KEY = "animeflow:auth:session";

    /**
     * 用户活跃会话索引，完整键为 animeflow:auth:user:sessions:{userId}，值为 sessionId 集合
     */
    public static final String AUTH_USER_SESSIONS_KEY = "animeflow:auth:user:sessions";

    /**
     * user_oauth.platform 字段：Bangumi 第三方登录
     */
    public static final String BANGUMI_OAUTH_PLATFORM = "bangumi";

    /**
     * 新用户默认头像
     */
    public static final String DEFAULT_USER_AVATAR_URL =
            "https://wsrv.nl/?url=https://raw.githubusercontent.com/openAnimeFlow/animeFlow-assets/main/image/default_avatar.webp";
}
