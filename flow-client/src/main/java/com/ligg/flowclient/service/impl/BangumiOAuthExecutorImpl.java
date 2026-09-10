package com.ligg.flowclient.service.impl;

import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.exception.LoginExpiredException;
import com.ligg.common.exception.BangumiAuthorizationException;
import com.ligg.flowclient.service.BangumiOAuthExecutor;
import com.ligg.flowclient.service.BangumiOAuthTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class BangumiOAuthExecutorImpl implements BangumiOAuthExecutor {

    private final BangumiOAuthTokenService bangumiOAuthTokenService;

    @Override
    public <T> T execute(UserOauthEntity oauth, Function<String, T> apiCall) {
        try {
            return apiCall.apply(oauth.getAccessToken());
        } catch (LoginExpiredException e) {
            bangumiOAuthTokenService.refreshBangumiAccessToken(oauth);
            try {
                return apiCall.apply(oauth.getAccessToken());
            } catch (LoginExpiredException retryError) {
                throw new BangumiAuthorizationException(retryError);
            }
        }
    }

    @Override
    public <T> T execute(Long userId, Function<String, T> apiCall) {
        UserOauthEntity oauth = bangumiOAuthTokenService.requireBangumiOauth(userId);
        return execute(oauth, apiCall);
    }
}
