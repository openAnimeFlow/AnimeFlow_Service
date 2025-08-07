package com.ligg.service;

import com.ligg.module.response.AccessToken;
import org.springframework.http.HttpEntity;

/**
 * @Author Ligg
 * @Time 2025/8/7
 **/
public interface TokenService {
    /**
     * 获取token
     * @param code
     * @return 获取到的token
     */
    AccessToken getToken(String code);
}
