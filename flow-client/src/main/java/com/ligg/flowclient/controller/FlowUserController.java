/**
 * @author Ligg
 * @date 2026/6/8 10:25
 */

package com.ligg.flowclient.controller;

import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.module.vo.UserVo;
import com.ligg.common.vo.bangumi.UserCollectionsVo;
import com.ligg.flowclient.service.UserBgmCollectionService;
import com.ligg.flowclient.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class FlowUserController {

    private final UserService userService;

    private final UserBgmCollectionService userBgmCollectionService;

    /**
     * 获取当前登录用户信息（需携带 AnimeFlow access_token）。
     */
    @GetMapping
    public Result<UserVo> getUserInfo(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken) {
        UserVo userVo = userService.getUserInfo(accessToken);
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
}
