package com.ligg.common.vo;

import java.time.LocalDateTime;

/**
 * 论坛评论列表单项（不含 IP 等敏感字段）。
 *
 * @param id         主键
 * @param userId     用户 id（一般为 Bangumi id）
 * @param parentId   父评论 id，0 表示一级评论
 * @param rootId     根评论 id；一级为 0，回复为所属一级评论 id
 * @param content    评论正文
 * @param imageUrl   图片地址
 * @param likeCount  点赞数
 * @param nickname   发布时昵称
 * @param avatarUrl  发布时头像
 * @param createTime 创建时间
 * @author Ligg
 */
public record ForumCommentListItemVO(
        Long id,
        Long userId,
        Long parentId,
        Long rootId,
        String content,
        String imageUrl,
        Integer likeCount,
        String nickname,
        String avatarUrl,
        LocalDateTime createTime
) {
}
