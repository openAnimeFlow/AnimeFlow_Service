package com.ligg.flowclient.service;

import jakarta.validation.constraints.NotBlank;

public interface EmailService {


    /**
     * 校验邮箱验证码，通过后删除 Redis 中的验证码（一次性）。
     *
     * @param email   邮箱
     * @param captcha 验证码
     */
    void verifyEmailCode(@NotBlank String email, @NotBlank String captcha);

    /**
     * 校验该邮箱是否处于发送冷却期（仅在上次发送成功后生效）。
     */
    void checkSendCooldown(@NotBlank String email);

    /**
     * 标记该邮箱已成功发送，进入冷却期。
     */
    void markSendSuccess(@NotBlank String email);
}
