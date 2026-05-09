/**
 * @author Ligg
 * @date 2026/5/3 16:59
 */
package com.ligg.flowclient.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ligg.common.vo.ForumCommentListItemVO;
import com.ligg.common.vo.ForumCommentReplyVO;
import com.ligg.common.vo.PageVO;
import com.ligg.flowclient.mapper.ForumCommentMapper;
import com.ligg.flowclient.module.entity.ForumCommentEntity;
import com.ligg.flowclient.service.ForumCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ForumCommentServiceImpl implements ForumCommentService {

    private final ForumCommentMapper forumCommentMapper;

    @Override
    public int addComment(ForumCommentEntity forumComment) {
        Long pid = forumComment.getParentId();
        if (pid != null && pid != 0L) {
            ForumCommentEntity parent = forumCommentMapper.selectById(pid);
            if (parent == null || parent.getParentId() == null || parent.getParentId() != 0L) {
                throw new IllegalArgumentException("仅允许回复一级评论");
            }
        }
        return forumCommentMapper.insert(forumComment);
    }

    @Override
    public PageVO<ForumCommentListItemVO> pageTopLevelComments(long current, long size) {

        LambdaQueryWrapper<ForumCommentEntity> wrapper = new LambdaQueryWrapper<ForumCommentEntity>()
                .eq(ForumCommentEntity::getParentId, 0L)
                .orderByDesc(ForumCommentEntity::getCreateTime);

        IPage<ForumCommentEntity> entityPage = forumCommentMapper.selectPage(new Page<>(current, size), wrapper);
        List<ForumCommentEntity> tops = entityPage.getRecords();
        List<Long> topIds = tops.stream().map(ForumCommentEntity::getId).toList();

        final Map<Long, List<ForumCommentEntity>> repliesByParent;
        if (topIds.isEmpty()) {
            repliesByParent = Map.of();
        } else {
            List<ForumCommentEntity> replyRows = forumCommentMapper.selectRepliesByParentIds(topIds);
            repliesByParent = replyRows.stream().collect(Collectors.groupingBy(ForumCommentEntity::getParentId));
        }

        List<ForumCommentListItemVO> records = tops.stream()
                .map(e -> toThreadItem(e, repliesByParent.getOrDefault(e.getId(), List.of())))
                .toList();
        return new PageVO<>(
                records,
                entityPage.getTotal(),
                entityPage.getCurrent(),
                entityPage.getSize(),
                entityPage.getPages()
        );
    }

    private ForumCommentListItemVO toThreadItem(ForumCommentEntity e, List<ForumCommentEntity> replyEntities) {
        List<ForumCommentReplyVO> replyVos = replyEntities.stream()
                .map(this::toReplyVo)
                .toList();
        return new ForumCommentListItemVO(
                e.getId(),
                e.getUserId(),
                e.getParentId(),
                e.getContent(),
                e.getImageUrl(),
                e.getLikeCount(),
                e.getNickname(),
                e.getAvatarUrl(),
                e.getCreateTime(),
                replyVos
        );
    }

    private ForumCommentReplyVO toReplyVo(ForumCommentEntity e) {
        return new ForumCommentReplyVO(
                e.getId(),
                e.getUserId(),
                e.getParentId(),
                e.getContent(),
                e.getImageUrl(),
                e.getLikeCount(),
                e.getNickname(),
                e.getAvatarUrl(),
                e.getCreateTime()
        );
    }
}
