package com.ligg.module.response;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @Author Ligg
 * @Time 2025/8/7
 *
 * AccessToken 响应
 **/
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AccessToken extends TokenVo{

    /**
     * 用户id
     */
    private Integer user_id;
}
