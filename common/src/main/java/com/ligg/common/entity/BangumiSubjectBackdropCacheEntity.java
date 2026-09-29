package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 条目预览图
 */
@Data
@NoArgsConstructor
@TableName("bangumi_subject_backdrop_cache")
public class BangumiSubjectBackdropCacheEntity {

    @TableId(type = IdType.INPUT)
    private Integer subjectId;

    private String tmdbId;

    /** JSON array of backdrop URLs. */
    private String backdrops;

    /** JSON array of logo URLs. */
    private String logos;

    /** JSON array of poster URLs. */
    private String posters;

    /** The last successful refresh time. The cache is valid for seven days from this time. */
    private LocalDateTime fetchedAt;

    private LocalDateTime updatedAt;
}
