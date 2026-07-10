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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class BangumiOAuthTokenServiceImpl implements BangumiOAuthTokenService {

    private final UserOauthMapper userOauthMapper;
    private final BgmTvClient bgmTvClient;

    @Override
    public UserOauthEntity requireBangumiOauth(Long userId) {
        UserOauthEntity entity = findBangumiOauth(userId);
        if (entity == null) {
            throw new IllegalArgumentException("未绑定 Bangumi 账号");
        }
        return entity;
    }

    @Override
    public UserOauthEntity findBangumiOauth(Long userId) {
        UserOauthEntity entity = findByUserAndPlatform(userId);
        if (entity == null || !StringUtils.hasText(entity.getAccessToken())) {
            return null;
        }
        return entity;
    }

    @Override
    public void refreshBangumiAccessToken(UserOauthEntity oauth) {
        if (!StringUtils.hasText(oauth.getRefreshToken())) {
            deleteBangumiOauth(oauth, "missing refresh token");
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
            if (isInvalidRefreshToken(e)) {
                deleteBangumiOauth(oauth, "invalid refresh token");
            }
            throw new LoginExpiredException(e);
        }
    }

    private void deleteBangumiOauth(UserOauthEntity oauth, String reason) {
        LambdaQueryWrapper<UserOauthEntity> wrapper = new LambdaQueryWrapper<>();
        if (oauth.getId() != null) {
            wrapper.eq(UserOauthEntity::getId, oauth.getId());
        } else {
            wrapper.eq(UserOauthEntity::getUserId, oauth.getUserId())
                    .eq(UserOauthEntity::getPlatform, Constants.BANGUMI_OAUTH_PLATFORM);
        }
        wrapper.eq(UserOauthEntity::getPlatform, Constants.BANGUMI_OAUTH_PLATFORM);
        if (oauth.getRefreshToken() == null) {
            wrapper.isNull(UserOauthEntity::getRefreshToken);
        } else {
            wrapper.eq(UserOauthEntity::getRefreshToken, oauth.getRefreshToken());
        }
        int deleted = userOauthMapper.delete(wrapper);
        log.warn("Deleted Bangumi OAuth token after refresh failure: userId={} oauthId={} reason={} deleted={}",
                oauth.getUserId(), oauth.getId(), reason, deleted);
    }

    private static boolean isInvalidRefreshToken(BangumiUpstreamException e) {
        String message = e.getMessage();
        return message != null
                && (message.contains("invalid_grant") || message.contains("Invalid refresh token"));
    }

    private UserOauthEntity findByUserAndPlatform(Long userId) {
        LambdaQueryWrapper<UserOauthEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserOauthEntity::getUserId, userId)
                .eq(UserOauthEntity::getPlatform, Constants.BANGUMI_OAUTH_PLATFORM);
        return userOauthMapper.selectOne(wrapper);
    }
}
