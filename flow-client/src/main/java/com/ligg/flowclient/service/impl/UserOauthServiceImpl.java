package com.ligg.flowclient.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.api.bgmtvapi.BgmTvClient;
import com.ligg.common.constants.Constants;
import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.response.AccessToken;
import com.ligg.common.vo.BangumiUserinfoVO;
import com.ligg.flowclient.mapper.UserOauthMapper;
import com.ligg.flowclient.module.vo.BangumiBindVo;
import com.ligg.flowclient.service.UserOauthService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserOauthServiceImpl implements UserOauthService {

    private static final int STATUS_ACTIVE = 1;

    private final UserOauthMapper userOauthMapper;
    private final BgmTvClient bgmTvClient;
    private final BangumiClient bangumiClient;

    @Override
    public BangumiBindVo getBangumiBind(Long userId) {
        UserOauthEntity entity = findByUserAndPlatform(userId);
        if (entity == null) {
            return BangumiBindVo.notBound();
        }
        return new BangumiBindVo(
                true,
                entity.getPlatformUid(),
                null,
                null,
                null
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BangumiBindVo bindBangumi(Long userId, String code) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("授权码无效");
        }

        AccessToken token = bgmTvClient.exchangeToken(code);
        BangumiUserinfoVO me = bangumiClient.getMe(token.getAccess_token());
        Long platformUid = me.id().longValue();

        UserOauthEntity boundToOther = findByPlatformUid(platformUid);
        if (boundToOther != null && !userId.equals(boundToOther.getUserId())) {
            throw new IllegalArgumentException("该 Bangumi 账号已被其他用户绑定");
        }

        UserOauthEntity existing = findByUserAndPlatform(userId);
        if (existing != null && !platformUid.equals(existing.getPlatformUid())) {
            throw new IllegalArgumentException("当前账号已绑定其他 Bangumi 账号");
        }

        LocalDateTime now = LocalDateTime.now();
        long expireTime = Instant.now().getEpochSecond() + token.getExpires_in();

        if (existing != null) {
            existing.setPlatformUid(platformUid);
            existing.setAccessToken(token.getAccess_token());
            existing.setRefreshToken(token.getRefresh_token());
            existing.setExpireTime(expireTime);
            existing.setUpdateTime(now);
            existing.setStatus(STATUS_ACTIVE);
            userOauthMapper.updateById(existing);
        } else {
            UserOauthEntity entity = new UserOauthEntity();
            entity.setUserId(userId);
            entity.setPlatform(Constants.BANGUMI_OAUTH_PLATFORM);
            entity.setPlatformUid(platformUid);
            entity.setAccessToken(token.getAccess_token());
            entity.setRefreshToken(token.getRefresh_token());
            entity.setExpireTime(expireTime);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            entity.setStatus(STATUS_ACTIVE);
            try {
                userOauthMapper.insert(entity);
            } catch (DuplicateKeyException e) {
                throw new IllegalArgumentException("该 Bangumi 账号已被绑定");
            }
        }

        return toBindVo(me);
    }

    private UserOauthEntity findByUserAndPlatform(Long userId) {
        LambdaQueryWrapper<UserOauthEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserOauthEntity::getUserId, userId)
                .eq(UserOauthEntity::getPlatform, Constants.BANGUMI_OAUTH_PLATFORM);
        return userOauthMapper.selectOne(wrapper);
    }

    private UserOauthEntity findByPlatformUid(Long platformUid) {
        LambdaQueryWrapper<UserOauthEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserOauthEntity::getPlatform, Constants.BANGUMI_OAUTH_PLATFORM)
                .eq(UserOauthEntity::getPlatformUid, platformUid);
        return userOauthMapper.selectOne(wrapper);
    }

    private static BangumiBindVo toBindVo(BangumiUserinfoVO me) {
        String avatar = null;
        if (me.avatar() != null) {
            avatar = StringUtils.hasText(me.avatar().medium())
                    ? me.avatar().medium()
                    : me.avatar().large();
        }
        return new BangumiBindVo(
                true,
                me.id().longValue(),
                me.username(),
                me.nickname(),
                avatar
        );
    }
}
