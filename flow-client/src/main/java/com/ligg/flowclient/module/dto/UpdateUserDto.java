package com.ligg.flowclient.module.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class UpdateUserDto {

    @Size(min = 1, max = 100, message = "昵称长度必须在1-100之间")
    private String nickname;

    /**
     * 背景图 id
     */
    private Integer backgroundId;

    public boolean hasUpdateField() {
        return StringUtils.hasText(nickname) || backgroundId != null;
    }
}
