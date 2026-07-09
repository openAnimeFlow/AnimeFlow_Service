package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("user_episode_watch")
public class UserEpisodeWatchEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer subjectId;

    private Integer episodeId;

    /**
     * 0 未看，1 已看。
     */
    private Integer watchStatus;

    /**
     * 首次标记已看的时间。
     */
    private LocalDateTime watchedAt;

    private LocalDateTime updateTime;

    private LocalDateTime createTime;
}
