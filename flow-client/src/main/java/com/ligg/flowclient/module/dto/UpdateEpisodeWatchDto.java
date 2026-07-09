package com.ligg.flowclient.module.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateEpisodeWatchDto {

    @NotNull
    private Boolean watched;
}
