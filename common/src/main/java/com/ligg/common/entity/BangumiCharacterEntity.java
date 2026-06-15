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
@TableName("bangumi_character")
public class BangumiCharacterEntity {

    @TableId(type = IdType.INPUT)
    private Integer id;

    /**
     * 角色类型：1角色 2机体 3组织
     */
    private Integer role;

    /**
     * 角色名
     */
    private String name;

    /**
     * 原始 wiki 字符串
     */
    private String infobox;

    /**
     * 角色简介
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
