package com.ligg.flowclient.module.dto;

import com.ligg.common.statuenum.Platform;
import lombok.Data;

import java.io.Serializable;

/**
 * Redis 中存储的 AnimeFlow 登录会话，每个客户端平台独立一条。
 */
@Data
public class AuthSessionDto implements Serializable {

    private String sessionId;

    private Long userId;

    private String email;

    private Platform platform;

    private String accessJti;

    private String refreshJti;
}
