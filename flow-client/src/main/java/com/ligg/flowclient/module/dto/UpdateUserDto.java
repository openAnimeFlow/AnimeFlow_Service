package com.ligg.flowclient.module.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /** 仅供服务端在头像上传成功后写入，客户端不能直接指定头像 URL。 */
    @JsonIgnore
    private String avatar;

    public boolean hasUpdateField() {
        return hasBasicProfileUpdateField() || StringUtils.hasText(avatar);
    }

    public boolean hasBasicProfileUpdateField() {
        return StringUtils.hasText(nickname) || backgroundId != null;
    }
}
