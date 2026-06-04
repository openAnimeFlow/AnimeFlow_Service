/**
 * @author Ligg
 * @date 2026/6/5 01:22
 */
package com.ligg.flowclient.service.impl;

import cn.hutool.captcha.CircleCaptcha;
import com.ligg.flowclient.module.CaptchaResponse;
import com.ligg.flowclient.service.CaptchaService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    private final StringRedisTemplate redisTemplate;

    private final String CAPTCHA_KEY = "captcha:";

    /**
     * 生成图片验证码并存入Redis
     */
    @Override
    public CaptchaResponse generateCaptcha(@NotNull int codeCount, @NotNull int expirationDate) {
        // 宽、高、验证码位数、干扰圆圈数
        CircleCaptcha circleCaptcha = new CircleCaptcha(200, 50, codeCount, 15);
        String code = circleCaptcha.getCode();
        String imageBase64 = circleCaptcha.getImageBase64();

        String captchaId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(CAPTCHA_KEY + captchaId, code, expirationDate, TimeUnit.MINUTES);
        return new CaptchaResponse(captchaId, imageBase64);
    }

    @Override
    public boolean verifyCaptcha(String captchaId, String code) {
        String key = CAPTCHA_KEY + captchaId;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null || !stored.equalsIgnoreCase(code.trim())) {
            return false;
        }
        redisTemplate.delete(key);
        return true;
    }
}
