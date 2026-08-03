package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户番剧播放记录。
 */
@Data
@NoArgsConstructor
@TableName("user_play_history")
public class UserPlayHistoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
