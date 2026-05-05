package com.ligg.common.vo;

import java.time.LocalDateTime;

/**
 * 一级评论下的单层回复（不允许再嵌套 replies）。
 */
public record ForumCommentReplyVO(
        Long id,
        Long userId,
        Long parentId,
        String content,
        String imageUrl,
        Integer likeCount,
        String nickname,
        String avatarUrl,
        LocalDateTime createTime
) {
}
