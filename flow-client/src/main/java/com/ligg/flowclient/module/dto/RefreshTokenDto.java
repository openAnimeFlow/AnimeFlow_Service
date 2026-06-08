package com.ligg.flowclient.module.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenDto {

    @NotBlank(message = "refresh_token 不能为空")
    private String refreshToken;
}
