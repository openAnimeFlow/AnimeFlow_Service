package com.ligg.module.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Ligg
 * @create_time 2026/1/5 07:15
 * @update_time 2026/1/5 07:15
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenVo {

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

}
