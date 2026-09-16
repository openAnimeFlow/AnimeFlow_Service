package com.ligg.common.response;

public record GithubRelease(
        String name,
        String tag_name,
        String created_at,
        String body,
        String html_url) {
}
