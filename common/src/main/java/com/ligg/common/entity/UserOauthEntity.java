/**
 * @author Ligg
 * @date 2026/6/5 04:42
 */
package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDateTime;

@TableName("user_oauth")
public class UserOauthEntity {

    /**
     *
     */
    @NotNull(message = "[]不能为空")
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     *
     */
    private Long userId;
    /**
     *
     */
    private String platform;
    /**
     *
     */
    private Long platformUid;
    /**
     *
     */
    private String accessToken;
    /**
     *
     */
    @Size(max = 255, message = "编码长度不能超过255")
    @Length(max = 255, message = "编码长度不能超过255")
    private String refreshToken;
    /**
     *
     */
    private Long expireTime;
    /**
     *
     */
    private LocalDateTime createTime;
    /**
     *
     */
    private LocalDateTime updateTime;
    /**
     *
     */
    private Integer status;
}
