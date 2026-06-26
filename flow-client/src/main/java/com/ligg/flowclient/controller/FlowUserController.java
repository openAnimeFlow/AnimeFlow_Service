/**
 * @author Ligg
 * @date 2026/6/8 10:25
 */

package com.ligg.flowclient.controller;

import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.module.dto.UpdateUserCollectionDto;
import com.ligg.flowclient.module.dto.UpdateUserDto;
import com.ligg.flowclient.module.vo.FlowUserVo;
import com.ligg.common.vo.bangumi.UserCollectionsVo;
import com.ligg.flowclient.service.JwtTokenService;
import com.ligg.flowclient.service.UserBgmCollectionService;
import com.ligg.flowclient.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class FlowUserController {

    private final UserService userService;
    private final JwtTokenService jwtTokenService;
    private final UserBgmCollectionService userBgmCollectionService;

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
        Long userId = jwtTokenService.validateAccessToken(accessToken);
        FlowUserVo userVo = userService.uploadAvatar(userId, file);
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
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken,
            @RequestParam(defaultValue = "2") int subjectType,
            @RequestParam(defaultValue = "2") int type,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        UserCollectionsVo vo = userBgmCollectionService.listMyCollections(
                accessToken, subjectType, type, limit, offset);
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
}
