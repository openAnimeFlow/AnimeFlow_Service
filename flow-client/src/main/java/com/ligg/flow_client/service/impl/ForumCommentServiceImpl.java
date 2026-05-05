/**
 * @author Ligg
 * @date 2026/5/3 16:59
 */
package com.ligg.flow_client.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ligg.common.vo.ForumCommentListItemVO;
import com.ligg.common.vo.PageVO;
import com.ligg.flow_client.mapper.ForumCommentMapper;
import com.ligg.flow_client.module.entity.ForumCommentEntity;
import com.ligg.flow_client.service.ForumCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ForumCommentServiceImpl implements ForumCommentService {

    private final ForumCommentMapper forumCommentMapper;

    @Override
    public int addComment(ForumCommentEntity forumComment) {
        return forumCommentMapper.insert(forumComment);
    }

    @Override
    public PageVO<ForumCommentListItemVO> pageTopLevelComments(long current, long size) {
        long pageNo = current < 1 ? 1 : current;
        long pageSize = size < 1 ? 10 : Math.min(size, 100);

        LambdaQueryWrapper<ForumCommentEntity> wrapper = new LambdaQueryWrapper<ForumCommentEntity>()
                .eq(ForumCommentEntity::getParentId, 0L)
                .eq(ForumCommentEntity::getRootId, 0L)
                .orderByDesc(ForumCommentEntity::getCreateTime);

        IPage<ForumCommentEntity> entityPage = forumCommentMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<ForumCommentListItemVO> records = entityPage.getRecords().stream()
                .map(this::toListItem)
                .toList();
        return new PageVO<>(
                records,
                entityPage.getTotal(),
                entityPage.getCurrent(),
                entityPage.getSize(),
                entityPage.getPages()
        );
    }

    private ForumCommentListItemVO toListItem(ForumCommentEntity e) {
        return new ForumCommentListItemVO(
                e.getId(),
                e.getUserId(),
                e.getParentId(),
                e.getRootId(),
                e.getContent(),
                e.getImageUrl(),
                e.getLikeCount(),
                e.getNickname(),
                e.getAvatarUrl(),
                e.getCreateTime()
        );
    }
}
