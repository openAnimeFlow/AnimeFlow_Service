package com.ligg.flowclient.module.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SavePlayHistoryDto {

    @NotNull
    @Min(1)
    private Integer subjectId;

    @NotNull
    @Min(1)
    private Integer episodeId;

    @NotNull
    @Min(0)
    private Integer episodeSort;

    @NotBlank
    private String subjectName;

    @NotBlank
    private String cover;

    @NotNull
    private List<String> alias;

    @NotNull
    @Min(0)
    private Integer positionSeconds;

    @NotNull
    @Min(0)
    private Integer durationSeconds;
}
