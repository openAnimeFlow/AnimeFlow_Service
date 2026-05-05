/**
 * @author Ligg
 * @date 2026/5/3 16:53
 */
package com.ligg.flow_client.module.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("forum_comment")
public class ForumCommentEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

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

    /**
     * 发布评论时的Bangumi昵称
     */
    private String nickname;

    /**
     * 发布评论时的Bangumi头像URL
     */
    private String avatarUrl;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 发布评论时的IP地址
     */
    private String ipAddress;

    /**
     * 评论创建时间
     */
    private LocalDateTime createTime;
}
