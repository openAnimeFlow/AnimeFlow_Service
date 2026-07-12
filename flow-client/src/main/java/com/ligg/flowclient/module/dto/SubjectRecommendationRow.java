package com.ligg.flowclient.module.dto;

import lombok.Data;

/**
 * bangumi_subject 相似推荐查询结果行。
 */
@Data
public class SubjectRecommendationRow {

    private Integer id;
    private Integer type;
    private String name;
    private String nameCn;
    private String infobox;
    private Boolean nsfw;
    private Double score;
    private String scoreDetails;
    private Integer rank;
    private String images;
}
