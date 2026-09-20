package com.ligg.flowclient.module.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CollectionConflictResolveDto {
    private String requestId;
    @NotEmpty
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull private Long conflictId;
        @NotNull private Long conflictVersion;
        @NotNull private Integer selectedType;
    }
}
