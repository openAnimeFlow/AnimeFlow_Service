/**
 * @author Ligg
 * @date 2026/6/5 01:17
 */
package com.ligg.flowclient.service;

import com.ligg.flowclient.module.CaptchaResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

/**
 * 验证码服务
 */
@Validated
public interface CaptchaService {

    /**
     * 生成图片验证码
     *
     * @param codeCount      验证码长度
     * @param expirationDate 过期世家（秒）
     */
    CaptchaResponse generateCaptcha( @NotNull int codeCount, @NotNull int expirationDate);

    /**
     * 校验图片验证码；校验成功后删除 Redis 中的记录（一次性使用）。
     *
     * @param captchaId 生成接口返回的验证码 id
     * @param code      用户输入的验证码
     * @return 校验是否通过
     */
    boolean verifyCaptcha(@NotBlank String captchaId, @NotBlank String code);
}
