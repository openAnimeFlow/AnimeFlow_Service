package com.ligg.flowclient.module.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PlayHistoryVo {

    private Long id;
    private Integer subjectId;
    private Integer episodeId;
    private Integer episodeSort;
    private String subjectName;
    private String cover;
    private List<String> alias;
    private Integer positionSeconds;
    private Integer durationSeconds;
    private Boolean completed;
    private LocalDateTime completedAt;
    private LocalDateTime lastPlayedAt;
}
