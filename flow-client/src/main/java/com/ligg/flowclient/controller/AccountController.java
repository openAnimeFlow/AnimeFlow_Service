/**
 * @author Ligg
 * @date 2026/6/5 03:54
 */
package com.ligg.flowclient.controller;

import com.ligg.common.response.Result;
import com.ligg.flowclient.annotation.IpEndpointRateLimit;
import com.ligg.flowclient.module.dto.RegisterDto;
import com.ligg.flowclient.service.EmailService;
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

    /**
     * 注册账户
     */
    @PostMapping("/register")
    @IpEndpointRateLimit(keyPrefix = "animeflow:account:register:ip:", seconds = 60, maxRequests = 10)
    public Result<Void> register(@Valid @RequestBody RegisterDto registerDto) {
        emailService.verifyEmailCode(registerDto.getEmail(), registerDto.getEmailCode());
        userService.register(registerDto);
        return Result.success();
    }
}
