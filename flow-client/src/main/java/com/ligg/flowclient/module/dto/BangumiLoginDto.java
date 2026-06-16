package com.ligg.flowclient.module.dto;

import com.ligg.common.statuenum.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BangumiLoginDto {

    @NotBlank(message = "授权码不能为空")
    @Size(max = 100, message = "授权码无效")
    private String code;

    @NotNull(message = "平台不能为空")
    private Platform platform;
}
