/**
 * @author Ligg
 * @date 2026/6/5 03:36
 */
package com.ligg.flowclient.module.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendEmailDto {
    @NotBlank(message = "邮箱不能为空")
    private String email;
    @NotBlank
    private String captchaId;
    @NotBlank
    private String captcha;
}
