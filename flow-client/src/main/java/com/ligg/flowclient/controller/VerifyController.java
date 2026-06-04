/**
 * @author Ligg
 * @date 2026/6/5 01:47
 */
package com.ligg.flowclient.controller;

import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.flowclient.module.CaptchaResponse;
import com.ligg.flowclient.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/verify")
public class VerifyController {

    private final CaptchaService captchaService;

    /**
     * 生成图片验证码
     */
    @PostMapping("/captcha")
    public Result<CaptchaResponse> generateCaptcha() {
        return Result.success(ResponseCode.SUCCESS, captchaService.generateCaptcha(6, 6));
    }
}
