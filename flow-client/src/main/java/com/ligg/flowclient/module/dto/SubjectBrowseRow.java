package com.ligg.flowclient.module.dto;

import lombok.Data;

/**
 * 条目浏览列表的本地查询结果。
 */
@Data
public class SubjectBrowseRow {

    private Integer id;
    private Integer type;
    private String name;
    private String nameCn;
    private String infobox;
    private Boolean nsfw;
    private String metaTags;
    private Double score;
    private String scoreDetails;
    private Integer rank;
    private String images;
}
