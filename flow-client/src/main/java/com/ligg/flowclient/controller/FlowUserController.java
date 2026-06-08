/**
 * @author Ligg
 * @date 2026/6/8 10:25
 */

package com.ligg.flowclient.controller;

import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.module.vo.UserVo;
import com.ligg.flowclient.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class FlowUserController {

    private final UserService userService;

    /**
     * 获取当前登录用户信息（需携带 AnimeFlow access_token）。
     */
    @GetMapping
    public Result<UserVo> getUserInfo(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken) {
        UserVo userVo = userService.getUserInfo(accessToken);
        return Result.success(ResponseCode.SUCCESS, userVo);
    }
}
