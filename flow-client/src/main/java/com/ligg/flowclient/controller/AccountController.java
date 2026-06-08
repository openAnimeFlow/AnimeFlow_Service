/**
 * @author Ligg
 * @date 2026/6/5 03:54
 */
package com.ligg.flowclient.controller;

import com.ligg.common.response.FlowTokenVo;
import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.flowclient.annotation.IpEndpointRateLimit;
import com.ligg.flowclient.module.dto.LoginDto;
import com.ligg.flowclient.module.dto.RefreshTokenDto;
import com.ligg.flowclient.module.dto.RegisterDto;
import com.ligg.flowclient.service.EmailService;
import com.ligg.flowclient.service.JwtTokenService;
import com.ligg.flowclient.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
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
    @PostMapping("/login")
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
}
