package com.ligg.flowclient.module.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectReleaseAssetVo {

    private String name;
    private long size;
    private String browser_download_url;
}
