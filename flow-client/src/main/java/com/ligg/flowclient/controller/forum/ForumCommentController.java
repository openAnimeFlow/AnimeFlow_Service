/**
 * @author Ligg
 * @date 2026/5/3 16:36
 * 论坛评论控制器
 */
package com.ligg.flowclient.controller.forum;

import cn.hutool.extra.servlet.JakartaServletUtil;
import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.common.vo.BangumiUserinfoVO;
import com.ligg.common.vo.ForumCommentListItemVO;
import com.ligg.common.vo.PageVO;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.module.dto.ForumCommentDto;
import com.ligg.common.entity.ForumCommentEntity;
import com.ligg.common.response.Result;
import com.ligg.flowclient.service.ForumCommentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/forum/comment")
public class ForumCommentController {

    private final ForumCommentService forumCommentService;
    private final BangumiClient bangumiClient;

    /**
     * 发布评论
     */
    @PostMapping
    public Result<String> addComment(
            HttpServletRequest request,
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken,
            ForumCommentDto forumCommentDto) {
        // 从Bangumi中获取用户信息
        BangumiUserinfoVO me = bangumiClient.getMe(accessToken);

        ForumCommentEntity forumCommentEntity = new ForumCommentEntity();
        BeanUtils.copyProperties(forumCommentDto, forumCommentEntity);
        forumCommentEntity.setCreateTime(LocalDateTime.now());
        forumCommentEntity.setUserId(me.id());
        forumCommentEntity.setNickname(me.nickname());
        forumCommentEntity.setAvatarUrl(me.avatar().large());
        forumCommentEntity.setContent(forumCommentDto.getContent());
        forumCommentEntity.setIpAddress(JakartaServletUtil.getClientIP(request));
        forumCommentService.addComment(forumCommentEntity);
        log.info("评论发布成功，Bangumi {}", me);
        return Result.success();
    }

    /**
     * 获取评论列表（一级评论分页，无需登录）
     */
    @GetMapping("/list")
    public Result<PageVO<ForumCommentListItemVO>> getComments(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        PageVO<ForumCommentListItemVO> page = forumCommentService.pageTopLevelComments(current, size);
        return Result.success(ResponseCode.SUCCESS, page);
    }
}
