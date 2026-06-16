package com.ligg.flowclient.module.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class UpdateUserDto {

    @Size(min = 1, max = 100, message = "昵称长度必须在1-100之间")
    private String nickname;

    @Size(max = 500, message = "头像地址过长")
    private String avatar;

    public boolean hasUpdateField() {
        return StringUtils.hasText(nickname) || StringUtils.hasText(avatar);
    }
}
