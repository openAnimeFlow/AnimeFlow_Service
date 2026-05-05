/**
 * @author Ligg
 * @date 2026/5/3 17:09
 */
package com.ligg.module.dto;

import lombok.Data;

@Data
public class ForumCommentDto {
    /**
     * 父评论id，0表示一级评论
     */
    private Long parentId;

    /**
     * 根评论id；用于楼中楼结构查询，一级评论为0，回复评论时存所属一级评论ID
     */
    private Long rootId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 评论图片URL
     */
    private String imageUrl;

}
