package com.ligg.api.githubapi;

import com.ligg.common.response.GithubRelease;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
public interface GithubReleaseClient {

    GithubRelease getLatestRelease();

    List<GithubRelease> getReleases(
            @Min(value = 1, message = "参数异常")
            @Max(value = 100, message = "参数异常")
            long page);
}
