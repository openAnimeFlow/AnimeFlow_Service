package com.ligg.module.response;

import lombok.Data;

/**
 * @Author Ligg
 * @Time 2025/8/7
 *
 * AccessToken 响应
 **/
@Data
public class AccessToken {

    /**
     * 响应的AccessToken
     */
    private String access_token;

    /**
     * 过期时间
     */
    private Integer expires_in;

    /**
     * token类型
     */
    private String token_type;

    /**
     * 范围
     */
    private String scope;

    /**
     * 刷新token
     */
    private String refresh_token;

    /**
     * 用户id
     */
    private Integer user_id;

}
