package com.ligg.flowclient.module.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BindBangumiDto {

    @NotBlank(message = "授权码不能为空")
    @Size(max = 100, message = "授权码无效")
    private String code;
}
