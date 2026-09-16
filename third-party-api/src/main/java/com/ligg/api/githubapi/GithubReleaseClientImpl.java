package com.ligg.api.githubapi;

import com.ligg.api.config.WebClientConfig;
import com.ligg.common.apipath.ApiPath;
import com.ligg.common.response.GithubRelease;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Validated
@Service
public class GithubReleaseClientImpl implements GithubReleaseClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final ParameterizedTypeReference<List<GithubRelease>> RELEASE_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient githubClient;

    public GithubReleaseClientImpl(
            @Qualifier(WebClientConfig.GITHUB_WEB_CLIENT) WebClient githubClient) {
        this.githubClient = githubClient;
    }

    @Override
    public GithubRelease getLatestRelease() {
        GithubRelease release = githubClient.get()
                .uri(ApiPath.GITHUB_LATEST_RELEASE)
                .retrieve()
                .bodyToMono(GithubRelease.class)
                .block(REQUEST_TIMEOUT);
        if (release == null) {
            throw new IllegalStateException("GitHub latest release 响应为空");
        }
        validateRelease(release);
        return release;
    }

    @Override
    public List<GithubRelease> getReleases(long page) {
        List<GithubRelease> releases = githubClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(ApiPath.GITHUB_RELEASES)
                        .queryParam("page", page)
                        .build())
                .retrieve()
                .bodyToMono(RELEASE_LIST_TYPE)
                .block(REQUEST_TIMEOUT);
        if (releases == null) {
            throw new IllegalStateException("GitHub releases 响应为空");
        }
        validateReleases(releases);
        return releases;
    }

    private static void validateReleases(List<GithubRelease> releases) {
        for (GithubRelease release : releases) {
            validateRelease(release);
        }
    }

    private static void validateRelease(GithubRelease release) {
        if (release == null
                || release.tag_name() == null
                || release.created_at() == null
                || release.html_url() == null) {
            throw new IllegalStateException("GitHub release 缺少必要字段");
        }
    }
}
