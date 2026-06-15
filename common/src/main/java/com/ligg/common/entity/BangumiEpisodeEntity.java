/**
 * @author Ligg
 * @date 2026/6/15 12:03
 */
package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("bangumi_episode")
public class BangumiEpisodeEntity {

    @TableId(type = IdType.INPUT)
    private Integer id;

    /**
     * 章节名称
     */
    private String name;

    /**
     * 章节简体中文名
     */
    private String nameCn;

    /**
     * 章节介绍
     */
    private String description;

    /**
     * 播出时间
     */
    private String airdate;

    /**
     * 所在光盘序号
     */
    private Integer disc;

    /**
     * 播放时长
     */
    private String duration;

    /**
     * 作品 ID
     */
    private Integer subjectId;

    /**
     * 集数排序
     */
    private Integer sort;

    /**
     * 类型：0正篇 1特别篇 2OP 3ED 4Trailer 5MAD 6其他
     */
    private Integer type;
}
