package com.ligg.flowclient.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ligg.api.bgmtvapi.BgmTvClient;
import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.exception.BangumiUpstreamException;
import com.ligg.common.exception.LoginExpiredException;
import com.ligg.common.response.TokenVo;
import com.ligg.flowclient.mapper.UserOauthMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BangumiOAuthTokenServiceImplTest {

    @org.mockito.Mock
    private UserOauthMapper userOauthMapper;

    @org.mockito.Mock
    private BgmTvClient bgmTvClient;

    @Test
    void refreshBangumiAccessToken_deletesOauthWhenRefreshTokenIsInvalid() {
        BangumiOAuthTokenServiceImpl service = new BangumiOAuthTokenServiceImpl(userOauthMapper, bgmTvClient);
        UserOauthEntity oauth = oauth("old-refresh");
        when(bgmTvClient.refreshToken("old-refresh"))
                .thenThrow(new BangumiUpstreamException("""
                        刷新 token 失败: {"error":"invalid_grant","error_description":"Invalid refresh token"}
                        """));

        assertThrows(LoginExpiredException.class, () -> service.refreshBangumiAccessToken(oauth));

        verify(userOauthMapper).delete(anyOauthWrapper());
        verify(userOauthMapper, never()).updateById(any(UserOauthEntity.class));
    }

    @Test
    void refreshBangumiAccessToken_deletesOauthWhenRefreshTokenIsMissing() {
        BangumiOAuthTokenServiceImpl service = new BangumiOAuthTokenServiceImpl(userOauthMapper, bgmTvClient);
        UserOauthEntity oauth = oauth(null);

        assertThrows(LoginExpiredException.class, () -> service.refreshBangumiAccessToken(oauth));

        verify(userOauthMapper).delete(anyOauthWrapper());
        verify(userOauthMapper, never()).updateById(any(UserOauthEntity.class));
    }

    @Test
    void refreshBangumiAccessToken_keepsOauthWhenRefreshFailsTemporarily() {
        BangumiOAuthTokenServiceImpl service = new BangumiOAuthTokenServiceImpl(userOauthMapper, bgmTvClient);
        UserOauthEntity oauth = oauth("old-refresh");
        when(bgmTvClient.refreshToken("old-refresh"))
                .thenThrow(new BangumiUpstreamException("bgm.tv 响应超时，请稍后重试"));

        assertThrows(LoginExpiredException.class, () -> service.refreshBangumiAccessToken(oauth));

        verify(userOauthMapper, never()).delete(anyOauthWrapper());
        verify(userOauthMapper, never()).updateById(any(UserOauthEntity.class));
    }

    @Test
    void refreshBangumiAccessToken_updatesOauthWhenRefreshSucceeds() {
        BangumiOAuthTokenServiceImpl service = new BangumiOAuthTokenServiceImpl(userOauthMapper, bgmTvClient);
        UserOauthEntity oauth = oauth("old-refresh");
        TokenVo token = new TokenVo("new-access", 3600, "Bearer", null, "new-refresh");
        when(bgmTvClient.refreshToken("old-refresh")).thenReturn(token);

        service.refreshBangumiAccessToken(oauth);

        assertEquals("new-access", oauth.getAccessToken());
        assertEquals("new-refresh", oauth.getRefreshToken());
        verify(userOauthMapper).updateById(oauth);
        verify(userOauthMapper, never()).delete(anyOauthWrapper());
    }

    private static UserOauthEntity oauth(String refreshToken) {
        UserOauthEntity oauth = new UserOauthEntity();
        oauth.setId(10L);
        oauth.setUserId(20L);
        oauth.setPlatform("bangumi");
        oauth.setAccessToken("old-access");
        oauth.setRefreshToken(refreshToken);
        return oauth;
    }

    @SuppressWarnings("unchecked")
    private static Wrapper<UserOauthEntity> anyOauthWrapper() {
        return any(Wrapper.class);
    }
}
