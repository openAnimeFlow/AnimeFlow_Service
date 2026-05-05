package com.ligg.flow_client.service;

import com.ligg.common.response.AccessToken;
import com.ligg.common.response.TokenVo;

/**
 * @Author Ligg
 * @Time 2025/8/7
 **/
public interface OAuthService {
    /**
     * 获取token
     * @param code
     * @return 获取到的token
     */
    AccessToken getToken(String code);

    /**
     * 刷新token
     */
    TokenVo refreshToken(String refreshToken);
}
