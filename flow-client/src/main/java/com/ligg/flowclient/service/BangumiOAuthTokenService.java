package com.ligg.flowclient.service;

import com.ligg.common.entity.UserOauthEntity;

/**
 * Bangumi OAuth 绑定与 token 持久化；不含上游调用与 401 重试（见 {@link BangumiOAuthExecutor}）。
 */
public interface BangumiOAuthTokenService {

    /**
     * 获取用户 Bangumi OAuth 绑定，未绑定则抛异常。
     */
    UserOauthEntity requireBangumiOauth(Long userId);

    /**
     * 获取用户 Bangumi OAuth 绑定；未绑定或 access_token 为空时返回 {@code null}。
     */
    UserOauthEntity findBangumiOauth(Long userId);

    /**
     * 使用 refresh_token 刷新 Bangumi access_token 并写回 user_oauth。
     */
    void refreshBangumiAccessToken(UserOauthEntity oauth);
}
