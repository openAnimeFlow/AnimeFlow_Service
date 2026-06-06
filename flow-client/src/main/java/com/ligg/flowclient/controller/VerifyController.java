/**
 * @author Ligg
 * @date 2026/6/5 01:47
 */
package com.ligg.flowclient.controller;

import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.flowclient.annotation.IpEndpointRateLimit;
import com.ligg.flowclient.module.CaptchaResponse;
import com.ligg.flowclient.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/verify")
public class VerifyController {

    private final CaptchaService captchaService;

    /**
     * 生成图片验证码；若传入 captchaId 则先删除上次验证码。
     * 一个 ip 一分钟最多 20 次
     */
    @PostMapping("/captcha")
    @IpEndpointRateLimit(keyPrefix = "animeflow:captcha:send:ip:", seconds = 60, maxRequests = 20)
    public Result<CaptchaResponse> generateCaptcha(
            @RequestParam(required = false) String captchaId) {
        captchaService.deleteCaptcha(captchaId);
        return Result.success(ResponseCode.SUCCESS, captchaService.generateCaptcha(6, 6));
    }
}
