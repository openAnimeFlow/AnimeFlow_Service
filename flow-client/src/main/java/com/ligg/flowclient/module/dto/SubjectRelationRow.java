package com.ligg.flowclient.module.dto;

import lombok.Data;

/**
 * bangumi_subject_relation 与 bangumi_subject 关联查询的结果行。
 */
@Data
public class SubjectRelationRow {

    /**
     * 关联源条目 ID
     */
    private Integer subjectId;

    /**
     * 关联类型
     */
    private Integer relationType;

    /**
     * 关联排序
     */
    private Integer order;

    /* ---------- bangumi_subject 字段 ---------- */

    private Integer id;
    private Integer type;
    private String name;
    private String nameCn;
    private String infobox;
    private Integer platform;
    private String summary;
    private Boolean nsfw;
    private String tags;
    private String metaTags;
    private Double score;
    private String scoreDetails;
    private Integer rank;
    private String date;
    private String favorite;
    private Boolean series;
    private String images;
}
