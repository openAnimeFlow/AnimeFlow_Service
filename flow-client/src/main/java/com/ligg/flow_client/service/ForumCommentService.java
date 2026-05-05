package com.ligg.flow_client.service;

import com.ligg.common.vo.ForumCommentListItemVO;
import com.ligg.common.vo.PageVO;
import com.ligg.flow_client.module.entity.ForumCommentEntity;

public interface ForumCommentService {

    /**
     * 添加评论
     * @return 添加数量
     */
    int addComment(ForumCommentEntity forumComment);

    /**
     * 分页查询评论
     */
    PageVO<ForumCommentListItemVO> pageTopLevelComments(long current, long size);
}
