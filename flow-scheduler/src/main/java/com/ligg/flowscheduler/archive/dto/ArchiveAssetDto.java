package com.ligg.flowscheduler.archive.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * latest.json 中单个 Release 资源（jsonlines 文件）的下载信息。
 *
 * @author Ligg
 */
@Data
public class ArchiveAssetDto {

    private String name;

    @JsonProperty("browser_download_url")
    private String browserDownloadUrl;

    private Long size;

    @JsonProperty("content_type")
    private String contentType;
}
