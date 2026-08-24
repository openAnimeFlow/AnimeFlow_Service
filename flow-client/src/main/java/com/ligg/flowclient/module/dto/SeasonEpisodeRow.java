package com.ligg.flowclient.module.dto;

import lombok.Data;

/**
 * bangumi_episode 季番正片章节查询结果行。
 */
@Data
public class SeasonEpisodeRow {

    private Integer subjectId;
    private Integer id;
    private String name;
    private String nameCn;
    private Integer sort;
    private String airdate;
    private String duration;
}
