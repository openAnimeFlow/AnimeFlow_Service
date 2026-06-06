/**
 * @author Ligg
 * @date 2026/6/4 13:16
 */
package com.ligg.flowclient.controller;

import com.ligg.api.resend.ResendClient;
import com.ligg.common.constants.Constants;
import com.ligg.common.exception.EmailSendException;
import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.flowclient.annotation.IpEndpointRateLimit;
import com.ligg.flowclient.module.dto.SendEmailDto;
import com.ligg.flowclient.service.CaptchaService;
import com.ligg.flowclient.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/email")
public class EmailController {

    private static final int CODE_EXPIRE_MINUTES = 6;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ResendClient resendClient;

    private final RedisTemplate<String, Object> redisTemplate;

    private final CaptchaService captchaService;

    private final EmailService emailService;

    /**
     * 发送邮件验证码
     * 一个 ip 一分钟最多 5 次
     */
    @PostMapping("/send")
    @IpEndpointRateLimit(keyPrefix = "animeflow:email:send:ip:", seconds = 60, maxRequests = 5)
    public Result<String> sendEmail(@Valid SendEmailDto sendEmailDto) {
        if (!captchaService.verifyCaptcha(sendEmailDto.getCaptchaId(), sendEmailDto.getCaptcha())) {
            throw new EmailSendException("验证码错误");
        }

        final String email = sendEmailDto.getEmail();
        emailService.checkSendCooldown(email);

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        String emailId = resendClient.sendEmail(email, code, CODE_EXPIRE_MINUTES);
        redisTemplate.opsForValue().set(
                Constants.EMAIL_VERIFICATION_KEY + ':' + email,
                code,
                CODE_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );
        emailService.markSendSuccess(email);
        return Result.success(ResponseCode.SUCCESS, emailId);
    }
}
