package com.ligg.flowclient.module.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordDto {

    @NotBlank(message = "旧密码不能为空")
    @Size(min = 6, max = 30, message = "旧密码长度必须在6-30之间")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 30, message = "新密码长度必须在6-30之间")
    private String newPassword;
}
