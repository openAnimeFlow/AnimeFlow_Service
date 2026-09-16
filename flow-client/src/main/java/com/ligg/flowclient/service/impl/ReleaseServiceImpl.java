package com.ligg.flowclient.service.impl;

import com.ligg.common.response.GithubRelease;
import com.ligg.api.githubapi.GithubReleaseClient;
import com.ligg.flowclient.module.vo.ProjectReleaseVo;
import com.ligg.flowclient.service.CacheService;
import com.ligg.flowclient.service.ReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReleaseServiceImpl implements ReleaseService {

    private static final String CACHE_KEY_PREFIX = "anime-flow:project-releases:page:";
    private static final long CACHE_TTL_SECONDS = 3600L;

    private final GithubReleaseClient githubReleaseClient;
    private final CacheService cacheService;

    @Override
    public List<ProjectReleaseVo> getReleases(long page) {
        ProjectReleaseVo[] releases = cacheService.getOrLoad(
                CACHE_KEY_PREFIX + page,
                ProjectReleaseVo[].class,
                CACHE_TTL_SECONDS,
                "获取版本信息超时，请稍后重试",
                "获取版本信息被中断",
                () -> githubReleaseClient.getReleases(page).stream()
                        .map(ReleaseServiceImpl::toVo)
                        .toArray(ProjectReleaseVo[]::new));
        return Arrays.asList(releases);
    }

    private static ProjectReleaseVo toVo(GithubRelease release) {
        return new ProjectReleaseVo(
                release.name(),
                release.tag_name(),
                release.created_at(),
                release.body(),
                release.html_url());
    }
}
