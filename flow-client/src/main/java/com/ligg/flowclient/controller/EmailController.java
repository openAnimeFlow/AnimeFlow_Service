/**
 * @author Ligg
 * @date 2026/6/4 13:16
 */
package com.ligg.flowclient.controller;

import com.ligg.api.resend.ResendClient;
import com.ligg.common.constants.Constants;
import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/email")
public class EmailController {

    private static final int CODE_EXPIRE_MINUTES = 6;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ResendClient resendClient;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 发送邮件验证码
     */
    @PostMapping("/send")
    public Result<String> sendEmail(@NotNull(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email) {

        String emailCode = (String) redisTemplate.opsForValue().get(Constants.EMAIL_VERIFICATION_KEY + ':' + email);
        if (emailCode != null) {
            return Result.error(ResponseCode.TOO_MANY_REQUESTS);
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        String emailId = resendClient.sendEmail(email, code, CODE_EXPIRE_MINUTES);
        redisTemplate.opsForValue().set(
                Constants.EMAIL_VERIFICATION_KEY + ':' + email,
                code,
                CODE_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );
        return Result.success(ResponseCode.SUCCESS, String.valueOf(emailId));
    }
}
