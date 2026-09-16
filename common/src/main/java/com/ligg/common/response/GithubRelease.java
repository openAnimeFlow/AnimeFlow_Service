package com.ligg.common.response;

import java.util.List;

public record GithubRelease(
        String name,
        String tag_name,
        String created_at,
        String body,
        String html_url,
        List<GithubReleaseAsset> assets) {
}
