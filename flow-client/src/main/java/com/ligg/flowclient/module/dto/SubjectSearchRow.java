package com.ligg.flowclient.module.dto;

import lombok.Data;

/**
 * bangumi_subject_search 与 bangumi_subject 联合搜索结果行。
 */
@Data
public class SubjectSearchRow {

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
