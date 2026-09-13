/**
 * @author Ligg
 * @date 2026/6/8 10:25
 */
package com.ligg.flowclient.controller;

import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.flowclient.annotation.IpEndpointRateLimit;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.module.dto.ChangePasswordDto;
import com.ligg.flowclient.module.dto.UpdateEpisodeWatchDto;
import com.ligg.flowclient.module.dto.UpdateUserDto;
import com.ligg.flowclient.module.vo.FlowUserVo;
import com.ligg.flowclient.module.vo.SubjectEpisodeWatchStatusVo;
import com.ligg.flowclient.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class FlowUserController {

    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final UserService userService;

    private final UserEpisodeWatchService userEpisodeWatchService;

    /**
     * 获取当前登录用户信息（需携带 AnimeFlow access_token）。
     */
    @GetMapping
    public Result<FlowUserVo> getUserInfo(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken) {
        return Result.success(ResponseCode.SUCCESS,
                userService.getUserInfo(accessToken)
        );
    }

    /**
     * 更新当前登录用户资料
     */
    @PutMapping
    public Result<FlowUserVo> updateUserInfo(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken,
            @Valid @RequestBody UpdateUserDto body) {
        return Result.success(ResponseCode.SUCCESS,
                userService.updateUserInfo(accessToken, body)
        );
    }

    /**
     * 上传当前登录用户的头像
     * （支持 JPEG / PNG / WebP / GIF，最大 2MB）。
     */
    @PostMapping("/avatar")
    public Result<FlowUserVo> uploadAvatar(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("头像文件不能为空");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new IllegalArgumentException("头像文件大小不能超过 2MB");
        }
        if (!ALLOWED_AVATAR_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("仅支持 JPEG、PNG、WebP、GIF 格式的图片");
        }
        String avatarUrl = userService.uploadAvatar(file);
        UpdateUserDto body = new UpdateUserDto();
        body.setAvatar(avatarUrl);
        return Result.success(ResponseCode.SUCCESS,
                userService.updateUserInfo(accessToken, body)
        );
    }

    /**
     * 标记当前用户某一集已看/未看。
     */
    @PutMapping("/episodes/{episodeId}/watch")
    public Result<Void> updateEpisodeWatch(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken,
            @PathVariable int episodeId,
            @Valid @RequestBody UpdateEpisodeWatchDto body) {
        userEpisodeWatchService.updateEpisodeWatch(accessToken, episodeId, body);
        return Result.success();
    }

    /**
     * 标记当前用户某部番剧的全部剧集为已看。
     */
    @PutMapping("/subjects/{subjectId}/episodes/watch")
    public Result<Void> markSubjectEpisodesWatched(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken,
            @PathVariable int subjectId) {
        userEpisodeWatchService.markSubjectEpisodesWatched(accessToken, subjectId);
        return Result.success();
    }

    /**
     * 获取当前用户对某部作品的已看剧集列表。
     */
    @GetMapping("/subjects/{subjectId}/episodes/watch-status")
    public Result<SubjectEpisodeWatchStatusVo> getSubjectEpisodeWatchStatus(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken,
            @PathVariable int subjectId) {
        return Result.success(ResponseCode.SUCCESS,
                userEpisodeWatchService.getSubjectWatchStatus(accessToken, subjectId)
        );
    }

    /**
     * 修改密码。修改成功后会撤销该账号的全部登录会话.
     */
    @PutMapping("/password")
    @IpEndpointRateLimit(keyPrefix = "animeflow:account:change-password:ip:", seconds = 60, maxRequests = 10)
    public Result<Void> changePassword(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken,
            @Valid @RequestBody ChangePasswordDto body) {
        userService.changePassword(accessToken, body);
        return Result.success();
    }
}
