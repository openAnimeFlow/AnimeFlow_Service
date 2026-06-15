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
@TableName("bangumi_person")
public class BangumiPersonEntity {

    @TableId(type = IdType.INPUT)
    private Integer id;

    /**
     * 人物名
     */
    private String name;

    /**
     * 类型：1个人 2公司 3组合
     */
    private Integer type;

    /**
     * 人物职业列表 JSON
     */
    private String career;

    /**
     * 原始 wiki 字符串
     */
    private String infobox;

    /**
     * 人物简介
     */
    private String summary;

    /**
     * 评论/吐槽数
     */
    private Integer comments;

    /**
     * 收藏数
     */
    private Integer collects;
}
