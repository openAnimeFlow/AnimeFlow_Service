package com.ligg.flowclient.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ligg.api.bgmtvapi.BgmTvClient;
import com.ligg.common.constants.Constants;
import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.exception.BangumiUpstreamException;
import com.ligg.common.exception.LoginExpiredException;
import com.ligg.common.response.TokenVo;
import com.ligg.flowclient.mapper.UserOauthMapper;
import com.ligg.flowclient.service.BangumiOAuthTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BangumiOAuthTokenServiceImpl implements BangumiOAuthTokenService {

    private final UserOauthMapper userOauthMapper;
    private final BgmTvClient bgmTvClient;

    @Override
    public UserOauthEntity requireBangumiOauth(Long userId) {
        UserOauthEntity entity = findByUserAndPlatform(userId);
        if (entity == null || !StringUtils.hasText(entity.getAccessToken())) {
            throw new IllegalArgumentException("未绑定 Bangumi 账号");
        }
        return entity;
    }

    @Override
    public void refreshBangumiAccessToken(UserOauthEntity oauth) {
        if (!StringUtils.hasText(oauth.getRefreshToken())) {
            throw new LoginExpiredException();
        }
        try {
            TokenVo token = bgmTvClient.refreshToken(oauth.getRefreshToken());
            oauth.setAccessToken(token.getAccess_token());
            if (StringUtils.hasText(token.getRefresh_token())) {
                oauth.setRefreshToken(token.getRefresh_token());
            }
            oauth.setExpireTime(Instant.now().getEpochSecond() + token.getExpires_in());
            oauth.setUpdateTime(LocalDateTime.now());
            userOauthMapper.updateById(oauth);
        } catch (BangumiUpstreamException e) {
            throw new LoginExpiredException(e);
        }
    }

    private UserOauthEntity findByUserAndPlatform(Long userId) {
        LambdaQueryWrapper<UserOauthEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserOauthEntity::getUserId, userId)
                .eq(UserOauthEntity::getPlatform, Constants.BANGUMI_OAUTH_PLATFORM);
        return userOauthMapper.selectOne(wrapper);
    }
}
