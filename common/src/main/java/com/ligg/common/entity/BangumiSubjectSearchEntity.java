package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("bangumi_subject_search")
public class BangumiSubjectSearchEntity {

    @TableId(type = IdType.INPUT)
    private Integer subjectId;

    private Integer type;
    private Boolean nsfw;
    private String name;
    private String nameCn;
    private String aliases;
    private String tagsText;
    private String metaTagsText;
    private Integer year;
    private Integer platform;
    private Double score;
    private Integer subjectRank;
    private Integer favoriteDone;
    private String searchText;
}
