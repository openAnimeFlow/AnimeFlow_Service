package com.ligg.service;

import com.ligg.module.response.AccessToken;

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
}
