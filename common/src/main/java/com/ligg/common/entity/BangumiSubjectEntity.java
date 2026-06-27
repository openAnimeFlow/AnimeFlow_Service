/**
 * @author Ligg
 * @date 2026/6/15 12:03
 */
package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("bangumi_subject")
public class BangumiSubjectEntity {

    @TableId(type = IdType.INPUT)
    private Integer id;

    /**
     * 作品类型：1漫画 2动画 3音乐 4游戏 6三次元
     */
    private Integer type;

    /**
     * 条目名
     */
    private String name;

    /**
     * 条目简体中文名
     */
    private String nameCn;

    /**
     * 原始 wiki 字符串
     */
    private String infobox;

    /**
     * 条目平台
     */
    private Integer platform;

    /**
     * 条目简介
     */
    private String summary;

    /**
     * 是否 NSFW
     */
    private Boolean nsfw;

    /**
     * 标签列表 JSON [{name,count}]
     */
    private String tags;

    /**
     * 公共标签列表 JSON
     */
    private String metaTags;

    /**
     * 评分
     */
    private Double score;

    /**
     * 评分分布 JSON {1..10: count}
     */
    private String scoreDetails;

    /**
     * 类别内排名
     */
    @TableField("`rank`")
    private Integer rank;

    /**
     * 发行日期
     */
    private String date;

    /**
     * 收藏状态 JSON {wish,done,doing,on_hold,dropped}
     */
    private String favorite;

    /**
     * 是否为系列作品
     */
    private Boolean series;

    /**
     * 封面图片 JSON（large / common / medium / small / grid）
     */
    private String images;
}
