package com.ligg.flowclient.module.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** user_play_history 查询行。alias 保持 JSON 字符串，转换在 service 层完成。 */
@Data
public class UserPlayHistoryRow {

    private Long id;
    private Integer subjectId;
    private Integer episodeId;
    private Integer episodeSort;
    private String subjectName;
    private String cover;
    private String alias;
    private Integer positionSeconds;
    private Integer durationSeconds;
    private Boolean isCompleted;
    private LocalDateTime completedAt;
    private LocalDateTime lastPlayedAt;
}
