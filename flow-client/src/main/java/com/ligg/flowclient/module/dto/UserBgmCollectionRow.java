package com.ligg.flowclient.module.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏列表 JOIN 查询行：user_bgm_collection + bangumi_subject 必要字段。
 */
@Data
public class UserBgmCollectionRow {

    private Long id;
    private Long userId;
    private Integer subjectId;
    private Integer subjectType;
    private String images;
    private Long bgmInterestId;
    private Integer rate;
    private Integer type;
    private String comment;
    private String tags;
    private Integer epStatus;
    private Integer volStatus;
    private Boolean isPrivate;
    private Long bgmUpdatedAt;
    private LocalDateTime syncTime;
    private LocalDateTime createTime;

    private String subjectName;
    private String subjectNameCn;
    private Boolean nsfw;
    private Double score;
    private String scoreDetails;
    private Integer rank;
}
