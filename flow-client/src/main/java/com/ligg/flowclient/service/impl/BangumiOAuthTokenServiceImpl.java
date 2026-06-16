package com.ligg.flowclient.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ligg.common.constants.Constants;
import com.ligg.common.entity.UserOauthEntity;
import com.ligg.flowclient.mapper.UserOauthMapper;
import com.ligg.flowclient.service.BangumiOAuthTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BangumiOAuthTokenServiceImpl implements BangumiOAuthTokenService {

    private final UserOauthMapper userOauthMapper;

    @Override
    public UserOauthEntity requireBangumiOauth(Long userId) {
        UserOauthEntity entity = findByUserAndPlatform(userId);
        if (entity == null || !StringUtils.hasText(entity.getAccessToken())) {
            throw new IllegalArgumentException("未绑定 Bangumi 账号");
        }
        return entity;
    }

    private UserOauthEntity findByUserAndPlatform(Long userId) {
        LambdaQueryWrapper<UserOauthEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserOauthEntity::getUserId, userId)
                .eq(UserOauthEntity::getPlatform, Constants.BANGUMI_OAUTH_PLATFORM);
        return userOauthMapper.selectOne(wrapper);
    }
}
