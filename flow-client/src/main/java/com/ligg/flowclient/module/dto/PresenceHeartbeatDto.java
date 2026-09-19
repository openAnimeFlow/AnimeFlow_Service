package com.ligg.flowclient.module.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PresenceHeartbeatDto {

    @NotBlank
    @Size(max = 64)
    private String visitorId;

    @NotBlank
    @Size(max = 32)
    private String clientType;

    @Size(max = 32)
    private String appVersion;

    @NotBlank
    @Pattern(regexp = "online|watching|paused", message = "在线状态无效")
    private String status = "online";

    @Positive
    private Integer subjectId;

    @Positive
    private Integer episodeId;

    @Min(0)
    private Integer positionSeconds;
}
