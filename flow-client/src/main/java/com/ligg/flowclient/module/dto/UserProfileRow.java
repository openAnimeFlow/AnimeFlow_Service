package com.ligg.flowclient.module.dto;

import lombok.Data;

/**
 * 用户资料。
 */
@Data
public class UserProfileRow {

    private Long id;

    private String email;

    private String nickname;

    private String avatar;

    /**
     * 背景图 URL，未设置时为空字符串
     */
    private String background = "";

    private Long createTime;

    private int planToWatch;

    private int watched;

    private int watching;

    private int onHold;

    private int abandoned;
}
