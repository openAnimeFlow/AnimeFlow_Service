/**
 * @author Ligg
 * @date 2026/6/5 03:54
 */
package com.ligg.flowclient.controller;

import com.ligg.common.response.FlowTokenVo;
import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.flowclient.annotation.IpEndpointRateLimit;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.module.dto.BangumiLoginDto;
import com.ligg.flowclient.module.dto.BindBangumiDto;
import com.ligg.flowclient.module.dto.LoginDto;
import com.ligg.flowclient.module.dto.RefreshTokenDto;
import com.ligg.flowclient.module.dto.RegisterDto;
import com.ligg.flowclient.module.vo.BangumiBindVo;
import com.ligg.flowclient.service.EmailService;
import com.ligg.flowclient.service.JwtTokenService;
import com.ligg.flowclient.service.UserOauthService;
import com.ligg.flowclient.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/account")
public class AccountController {

    private final EmailService emailService;

    private final UserService userService;

    private final JwtTokenService jwtTokenService;

    private final UserOauthService userOauthService;

    /**
     * 注册账户
     */
    @PostMapping("/register")
    @IpEndpointRateLimit(keyPrefix = "animeflow:account:register:ip:", seconds = 60, maxRequests = 10)
    public Result<Void> register(@Valid @RequestBody RegisterDto registerDto) {
        emailService.verifyEmailCode(registerDto.getEmail(), registerDto.getEmailCaptcha());
        userService.register(registerDto);
        return Result.success();
    }

    /**
     * 邮箱密码登录
     */
    @PostMapping("/email/login")
    @IpEndpointRateLimit(keyPrefix = "animeflow:account:login:ip:", seconds = 60, maxRequests = 20)
    public Result<FlowTokenVo> login(@Valid @RequestBody LoginDto loginDto) {
        FlowTokenVo loginVo = userService.login(loginDto);
        return Result.success(ResponseCode.SUCCESS, loginVo);
    }

    /**
     * 使用 refresh_token 刷新 access_token（同时轮换 refresh_token）
     */
    @PostMapping("/refresh")
    @IpEndpointRateLimit(keyPrefix = "animeflow:account:refresh:ip:", seconds = 60, maxRequests = 30)
    public Result<FlowTokenVo> refresh(@Valid @RequestBody RefreshTokenDto refreshTokenDto) {
        FlowTokenVo loginVo = jwtTokenService.refreshToken(refreshTokenDto.getRefreshToken());
        return Result.success(ResponseCode.SUCCESS, loginVo);
    }

    /**
     * Bangumi 第三方授权登录
     * 未绑定本地账号时自动创建（邮箱/密码为空），返回 Flow token。
     */
    @PostMapping("/oauth/bangumi/login")
    @IpEndpointRateLimit(keyPrefix = "animeflow:account:bangumi-login:ip:", seconds = 60, maxRequests = 20)
    public Result<FlowTokenVo> loginBangumi(@Valid @RequestBody BangumiLoginDto bangumiLoginDto) {
        FlowTokenVo loginVo = userOauthService.loginBangumi(
                bangumiLoginDto.getCode(),
                bangumiLoginDto.getPlatform());
        return Result.success(ResponseCode.SUCCESS, loginVo);
    }

    /**
     * 查询当前 AnimeFlow 账号的 Bangumi 绑定状态。
     */
    @GetMapping("/oauth/bangumi")
    public Result<BangumiBindVo> getBangumiBind(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken) {
        Long userId = jwtTokenService.validateAccessToken(accessToken);
        BangumiBindVo bindVo = userOauthService.getBangumiBind(userId);
        return Result.success(ResponseCode.SUCCESS, bindVo);
    }

    /**
     * 绑定 Bangumi 账号（需已登录 AnimeFlow，Body 传 OAuth 授权码 code）。
     */
    @PostMapping("/oauth/bangumi/bind")
    @IpEndpointRateLimit(keyPrefix = "animeflow:account:bind-bangumi:ip:", seconds = 60, maxRequests = 10)
    public Result<BangumiBindVo> bindBangumi(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken,
            @Valid @RequestBody BindBangumiDto bindBangumiDto) {
        Long userId = jwtTokenService.validateAccessToken(accessToken);
        BangumiBindVo bindVo = userOauthService.bindBangumi(userId, bindBangumiDto.getCode());
        return Result.success(ResponseCode.SUCCESS, bindVo);
    }
}
