/**
 * @author Ligg
 * @date 2026/5/3 17:09
 */
package com.ligg.flow_client.module.dto;

import lombok.Data;

@Data
public class ForumCommentDto {
    /**
     * 父评论id，0表示一级评论
     */
    private Long parentId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 评论图片URL
     */
    private String imageUrl;

}
