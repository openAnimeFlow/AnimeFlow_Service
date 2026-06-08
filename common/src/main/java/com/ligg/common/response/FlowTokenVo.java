package com.ligg.common.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AnimeFlow 登录 / 刷新令牌响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowTokenVo {

    private String accessToken;

    private String tokenType = "Bearer";

    /**
     * access_token 有效期（秒）
     */
    private Long expiresIn;

    /**
     * 刷新令牌
     */
    private String refreshToken;

    /**
     * refresh_token 有效期（秒）
     */
    private Long refreshExpiresIn;

    /**
     * 会话 ID，同一客户端登录/刷新期间保持不变，不同平台/设备各自独立。
     */
    private String sessionId;
}
