package com.ligg.flowclient.service;

import com.ligg.common.entity.UserOauthEntity;

public interface BangumiOAuthTokenService {

    /**
     * 获取用户 Bangumi OAuth 绑定，未绑定则抛异常。
     */
    UserOauthEntity requireBangumiOauth(Long userId);
}
