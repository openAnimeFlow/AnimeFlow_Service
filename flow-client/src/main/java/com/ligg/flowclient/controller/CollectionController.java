/**
 * @author Ligg
 * @date 2026/7/11
 */
package com.ligg.flowclient.controller;

import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.common.vo.bangumi.UserCollectionsVo;
import com.ligg.flowclient.annotation.IpEndpointRateLimit;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.module.dto.UpdateUserCollectionDto;
import com.ligg.flowclient.module.vo.UserBgmCollectionSyncStatusVo;
import com.ligg.flowclient.service.BangumiOAuthTokenService;
import com.ligg.flowclient.service.JwtTokenService;
import com.ligg.flowclient.service.UserBgmCollectionService;
import com.ligg.flowclient.service.UserBgmCollectionSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/v1/users/collections")
@RequiredArgsConstructor
@RestController
public class CollectionController {

    private final JwtTokenService jwtTokenService;
    private final BangumiOAuthTokenService bangumiOAuthTokenService;
    private final UserBgmCollectionService userBgmCollectionService;
    private final UserBgmCollectionSyncService userBgmCollectionSyncService;


    /**
     * 获取当前用户收藏列表。
     *
     * @param subjectType 条目大类，默认 2（动画）
     * @param type        收藏状态，默认 2（看过）
     * @param keyword     条目名称关键字，可匹配原名或中文名
     * @param limit       每页条数，默认 20
     * @param offset      偏移量，默认 0
     */
    @GetMapping
    public Result<UserCollectionsVo> getMeCollections(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String flowAccessToken,
            @RequestParam(defaultValue = "2") int subjectType,
            @RequestParam(defaultValue = "2") int type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        Long userId = jwtTokenService.validateAccessToken(flowAccessToken);
        UserOauthEntity bangumiOauth = bangumiOAuthTokenService.findBangumiOauth(userId);
        String bangumiAccessToken = bangumiOauth != null ? bangumiOauth.getAccessToken() : null;
        UserCollectionsVo vo = userBgmCollectionService.listMyCollections(
                bangumiAccessToken, userId, subjectType, type, keyword, limit, offset);
        return Result.success(ResponseCode.SUCCESS, vo);
    }

    /**
     * 更新当前用户对条目的 Bangumi 收藏（需登录且已绑定 Bangumi）。
     */
    @PutMapping("/{subjectId}")
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
    @PostMapping("/sync")
    @IpEndpointRateLimit(keyPrefix = "animeflow:account:sync-bgm-collection:ip:", seconds = 60, maxRequests = 5)
    public Result<UserBgmCollectionSyncStatusVo> syncCollections(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken,
            @RequestParam(defaultValue = "2") int subjectType) {
        Long userId = jwtTokenService.validateAccessToken(accessToken);
        UserBgmCollectionSyncStatusVo status = userBgmCollectionSyncService.triggerSync(userId, subjectType);
        return Result.success(ResponseCode.SUCCESS, status);
    }

    /**
     * 查询 Bangumi 收藏同步任务状态。
     */
    @GetMapping("/sync")
    public Result<UserBgmCollectionSyncStatusVo> getCollectionSyncStatus(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken) {
        Long userId = jwtTokenService.validateAccessToken(accessToken);
        UserBgmCollectionSyncStatusVo status = userBgmCollectionSyncService.getSyncStatus(userId);
        return Result.success(ResponseCode.SUCCESS, status);
    }
}
