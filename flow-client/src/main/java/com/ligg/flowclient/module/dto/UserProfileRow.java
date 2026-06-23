package com.ligg.flowclient.module.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户资料。
 */
@Data
public class UserProfileRow {

    private Long id;

    private String email;

    private String nickname;

    private String avatar;

    private Long createTime;

    private int planToWatch;

    private int watched;

    private int watching;

    private int onHold;

    private int abandoned;
}
