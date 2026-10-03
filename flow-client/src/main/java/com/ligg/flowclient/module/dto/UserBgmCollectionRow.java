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
    private String remoteSyncStatus;
    private LocalDateTime localUpdatedAt;
    private LocalDateTime syncTime;
    private LocalDateTime createTime;

    private String name;
    private String nameCn;
    private Boolean nsfw;
    private Double score;
    private String scoreDetails;
    private Integer rank;
    private String infobox;

    /** 该条目正片总集数（bangumi_episode 中 type=0 的 sort 去重计数）。 */
    private Integer totalEpisodes;
    /** 用户观看到第几集：取本地连续观看进度 ep_status 与逐集观看记录的最大正片集号。 */
    private Integer watchedEpisode;
    /** 新番的下一集播出日期（yyyy-MM-dd），已完结或未收录播出日期时为 null。 */
    private String nextEpisodeAirDate;
}
