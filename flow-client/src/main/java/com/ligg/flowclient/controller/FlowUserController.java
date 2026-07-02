/**
 * @author Ligg
 * @date 2026/6/8 10:25
 */

package com.ligg.flowclient.controller;

import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.flowclient.annotation.IpEndpointRateLimit;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.module.dto.UpdateUserCollectionDto;
import com.ligg.flowclient.module.dto.UpdateUserDto;
import com.ligg.flowclient.module.vo.FlowUserVo;
import com.ligg.common.vo.bangumi.UserCollectionsVo;
import com.ligg.flowclient.module.vo.UserBgmCollectionSyncStatusVo;
import com.ligg.flowclient.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class FlowUserController {

    private final UserService userService;

    private final UserBgmCollectionService userBgmCollectionService;

    private final BangumiOAuthTokenService bangumiOAuthTokenService;

    private final UserBgmCollectionSyncService userBgmCollectionSyncService;

    private final JwtTokenService jwtTokenService;

    /**
     * 获取当前登录用户信息（需携带 AnimeFlow access_token）。
     */
    @GetMapping
    public Result<FlowUserVo> getUserInfo(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken) {
        FlowUserVo userVo = userService.getUserInfo(accessToken);
        return Result.success(ResponseCode.SUCCESS, userVo);
    }

    /**
     * 更新当前登录用户资料
     */
    @PutMapping
    public Result<FlowUserVo> updateUserInfo(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken,
            @Valid @RequestBody UpdateUserDto body) {
        FlowUserVo userVo = userService.updateUserInfo(accessToken, body);
        return Result.success(ResponseCode.SUCCESS, userVo);
    }

    /**
     * 上传当前登录用户的头像
     * （支持 JPEG / PNG / WebP / GIF，最大 2MB）。
     */
    @PostMapping("/avatar")
    public Result<FlowUserVo> uploadAvatar(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken,
            @RequestParam("file") MultipartFile file) {
        FlowUserVo userVo = userService.uploadAvatar(accessToken, file);
        return Result.success(ResponseCode.SUCCESS, userVo);
    }

    /**
     * 获取当前用户收藏列表。
     *
     * @param subjectType 条目大类，默认 2（动画）
     * @param type        收藏状态，默认 2（看过）
     * @param limit       每页条数，默认 20
     * @param offset      偏移量，默认 0
     */
    @GetMapping("/collections")
    public Result<UserCollectionsVo> getMeCollections(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String flowAccessToken,
            @RequestParam(defaultValue = "2") int subjectType,
            @RequestParam(defaultValue = "2") int type,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        Long userId = jwtTokenService.validateAccessToken(flowAccessToken);
        UserOauthEntity bangumiOauth = bangumiOAuthTokenService.findBangumiOauth(userId);
        //accessToken 可能为null (未绑定bangumi)
        UserCollectionsVo vo = userBgmCollectionService.listMyCollections(
                bangumiOauth.getAccessToken(), userId, subjectType, type, limit, offset);
        return Result.success(ResponseCode.SUCCESS, vo);
    }

    /**
     * 更新当前用户对条目的 Bangumi 收藏（需登录且已绑定 Bangumi）。
     */
    @PutMapping("/collections/{subjectId}")
    public Result<Void> updateCollection(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken,
            @PathVariable int subjectId,
            @Valid @RequestBody UpdateUserCollectionDto body) {
        userBgmCollectionService.updateCollection(accessToken, subjectId, body);
        return Result.success();
    }

    /**
     * 提交 Bangumi 收藏同步任务（异步执行，立即返回任务状态）。
     * 从 user_oauth 读取 Bangumi token 拉取收藏并写入 user_bgm_collection。
     */
    @PostMapping("/collections/sync")
    @IpEndpointRateLimit(keyPrefix = "animeflow:account:sync-bgm-collection:ip:", seconds = 60, maxRequests = 5)
    public Result<UserBgmCollectionSyncStatusVo> syncBangumiCollections(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken,
            @RequestParam(defaultValue = "2") int subjectType) {
        Long userId = jwtTokenService.validateAccessToken(accessToken);
        UserBgmCollectionSyncStatusVo status = userBgmCollectionSyncService.triggerSync(userId, subjectType);
        return Result.success(ResponseCode.SUCCESS, status);
    }

    /**
     * 查询 Bangumi 收藏同步任务状态。
     */
    @GetMapping("/collections/sync")
    public Result<UserBgmCollectionSyncStatusVo> getBangumiCollectionSyncStatus(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken) {
        Long userId = jwtTokenService.validateAccessToken(accessToken);
        UserBgmCollectionSyncStatusVo status = userBgmCollectionSyncService.getSyncStatus(userId);
        return Result.success(ResponseCode.SUCCESS, status);
    }
}
