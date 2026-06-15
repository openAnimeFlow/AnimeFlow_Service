package com.ligg.flowscheduler.archive.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * animeFlow-assets {@code archive/latest.json} 的解析模型，描述当前最新 dump 及资源列表。
 *
 * @author Ligg
 */
@Data
public class ArchiveLatestDto {

    @JsonProperty("source_updated_at")
    private String sourceUpdatedAt;

    @JsonProperty("source_digest")
    private String sourceDigest;

    @JsonProperty("source_name")
    private String sourceName;

    @JsonProperty("dump_name")
    private String dumpName;

    private List<ArchiveAssetDto> assets;

    @JsonProperty("updated_at")
    private String updatedAt;
}
