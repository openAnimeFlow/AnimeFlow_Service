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
}
