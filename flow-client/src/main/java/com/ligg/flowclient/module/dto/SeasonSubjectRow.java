package com.ligg.flowclient.module.dto;

import lombok.Data;

/**
 * bangumi_subject 季番周表查询结果行。
 */
@Data
public class SeasonSubjectRow {

    private Integer id;
    private Integer type;
    private String name;
    private String nameCn;
    private String infobox;
    private Integer platform;
    private Boolean nsfw;
    private Double score;
    private String scoreDetails;
    private Integer rank;
    private String date;
    private String favorite;
    private String images;
    private String metaTags;
}
