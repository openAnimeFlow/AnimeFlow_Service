package com.ligg.common.response;

public record GithubReleaseAsset(
        String name,
        long size,
        String browser_download_url) {
}
