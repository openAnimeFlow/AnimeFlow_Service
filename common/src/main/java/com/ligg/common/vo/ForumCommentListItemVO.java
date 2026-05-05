package com.ligg.common.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 论坛评论列表单项（不含 IP 等敏感字段）。
 *
 * @param id         主键
 * @param userId     用户 id（一般为 Bangumi id）
 * @param parentId   父评论 id，0 表示一级评论
 * @param content    评论正文
 * @param imageUrl   图片地址
 * @param likeCount  点赞数
 * @param nickname   发布时昵称
 * @param avatarUrl  发布时头像
 * @param createTime 创建时间
 * @param replies    该条一级评论下的直接回复（一层，元素类型见 {@link ForumCommentReplyVO}）
 * @author Ligg
 */
public record ForumCommentListItemVO(
        Long id,
        Long userId,
        Long parentId,
        String content,
        String imageUrl,
        Integer likeCount,
        String nickname,
        String avatarUrl,
        LocalDateTime createTime,
        List<ForumCommentReplyVO> replies
) {
    public ForumCommentListItemVO {
        replies = replies == null ? List.of() : List.copyOf(replies);
    }
}
