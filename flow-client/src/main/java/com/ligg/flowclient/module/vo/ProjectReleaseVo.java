package com.ligg.flowclient.module.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * AnimeFlow GitHub Release 的客户端公开字段。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectReleaseVo {

    private String name;
    private String tag_name;
    private String created_at;
    private String body;
    private String html_url;
    private List<ProjectReleaseAssetVo> assets;
}
