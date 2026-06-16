package com.ligg.flowclient.service;

import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.exception.LoginExpiredException;

import java.util.function.Function;

/**
 * 携带 Bangumi OAuth 调用上游 API 的统一入口。
 * 遇 {@link LoginExpiredException}（Bangumi 401）时自动 refresh token 并重试一次，与 Flow JWT 无关。
 */
public interface BangumiOAuthExecutor {

    /**
     * 使用 {@code oauth} 中的 access_token 执行调用；401 时 refresh 后重试一次。
     */
    <T> T execute(UserOauthEntity oauth, Function<String, T> apiCall);

    /**
     * 按 AnimeFlow {@code userId} 加载绑定后执行，未绑定抛 {@link IllegalArgumentException}。
     */
    <T> T execute(Long userId, Function<String, T> apiCall);
}
