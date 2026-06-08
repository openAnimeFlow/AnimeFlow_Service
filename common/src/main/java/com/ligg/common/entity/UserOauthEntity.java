/**
 * @author Ligg
 * @date 2026/6/5 04:42
 */
package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("user_oauth")
public class UserOauthEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String platform;

    private Long platformUid;

    private String accessToken;

    private String refreshToken;

    private Long expireTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 1=有效，0=失效
     */
    private Integer status;
}
