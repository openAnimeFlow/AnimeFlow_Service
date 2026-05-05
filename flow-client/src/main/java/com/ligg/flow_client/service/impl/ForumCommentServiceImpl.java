/**
 * @author Ligg
 * @date 2026/5/3 16:59
 */
package com.ligg.flow_client.service.impl;

import com.ligg.flow_client.mapper.ForumCommentMapper;
import com.ligg.flow_client.service.ForumCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ForumCommentServiceImpl implements ForumCommentService {

    private final ForumCommentMapper forumCommentMapper;

    @Override
    public int addComment() {
        return 0;
    }
}
