package com.ligg.flowclient.service;

import com.ligg.flowclient.module.vo.ProjectReleaseVo;

import java.util.List;

public interface ReleaseService {

    List<ProjectReleaseVo> getReleases(long page);
}
