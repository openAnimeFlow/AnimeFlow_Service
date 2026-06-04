package com.ligg.flowclient.module.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CaptchaVerifyDto {

    @NotBlank(message = "验证码 id 不能为空")
    private String captchaId;

    @NotBlank(message = "验证码不能为空")
    private String code;
}
