/**
 * @author Ligg
 * @date 2026/6/5 04:28
 */
package com.ligg.flowclient.service.impl;

import com.ligg.common.constants.Constants;
import com.ligg.common.exception.CaptchaExpiredException;
import com.ligg.common.exception.RateLimitExceededException;
import com.ligg.common.exception.VerificationCodeException;
import com.ligg.flowclient.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private static final int SEND_COOLDOWN_SECONDS = 60;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 验证邮箱验证码
     */
    @Override
    public void verifyEmailCode(String email, String captcha) {
        String key = Constants.EMAIL_VERIFICATION_KEY + ':' + email;
        String code = (String) redisTemplate.opsForValue().get(key);
        if (code == null) {
            throw new CaptchaExpiredException("验证码已过期");
        }
        if (!code.equalsIgnoreCase(captcha)) {
            throw new VerificationCodeException("邮件验证码错误");
        }
        redisTemplate.delete(key);
    }

    @Override
    public void checkSendCooldown(String email) {
        String key = Constants.EMAIL_SEND_COOLDOWN_KEY + ':' + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            throw new RateLimitExceededException("发送过于频繁，请稍后再试");
        }
    }

    @Override
    public void markSendSuccess(String email) {
        String key = Constants.EMAIL_SEND_COOLDOWN_KEY + ':' + email;
        redisTemplate.opsForValue().set(key, "1", SEND_COOLDOWN_SECONDS, TimeUnit.SECONDS);
    }
}
