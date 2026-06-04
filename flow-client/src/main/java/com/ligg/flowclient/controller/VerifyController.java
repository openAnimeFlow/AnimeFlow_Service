/**
 * @author Ligg
 * @date 2026/6/5 01:47
 */
package com.ligg.flowclient.controller;

import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.flowclient.annotation.IpEndpointRateLimit;
import com.ligg.flowclient.module.CaptchaResponse;
import com.ligg.flowclient.module.dto.CaptchaVerifyDto;
import com.ligg.flowclient.service.CaptchaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    @IpEndpointRateLimit(keyPrefix = "animeflow:verify:captcha:ip:", seconds = 360)
    @PostMapping("/captcha")
    public Result<CaptchaResponse> generateCaptcha() {
        return Result.success(ResponseCode.SUCCESS, captchaService.generateCaptcha(6, 6));
    }

    /**
     * 校验图片验证码
     */
    @PostMapping("/captcha/verify")
    public Result<Void> verifyCaptcha(@Valid @RequestBody CaptchaVerifyDto dto) {
        if (!captchaService.verifyCaptcha(dto.getCaptchaId(), dto.getCode())) {
            return Result.error(ResponseCode.PARAM_ERROR, "验证码错误或已过期");
        }
        return Result.success();
    }
}
