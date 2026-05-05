/**
 * @author Ligg
 * @date 2026/5/3 16:36
 * 论坛评论控制器
 */
package com.ligg.controller.forum;

import com.ligg.interceptor.ForumCommentAuthorizationInterceptor;
import com.ligg.module.dto.ForumCommentDto;
import com.ligg.module.entity.ForumCommentEntity;
import com.ligg.module.response.Result;
import com.ligg.service.ForumCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/forum/comment")
public class ForumCommentController {

   private final ForumCommentService forumCommentService;

    /**
     * 发布评论
     */
    @PostMapping
    public Result<String> addComment(
            @RequestAttribute(ForumCommentAuthorizationInterceptor.AUTHORIZATION_REQUEST_ATTRIBUTE) String authorization,
            ForumCommentDto forumCommentDto) {
        ForumCommentEntity forumCommentEntity = new ForumCommentEntity();
        BeanUtils.copyProperties(forumCommentDto, forumCommentEntity);
        forumCommentEntity.setCreateTime(LocalDateTime.now());
        forumCommentService.addComment();
        return Result.success();
    }
}
