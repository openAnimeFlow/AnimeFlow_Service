package com.ligg.flowclient.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.api.bgmtvapi.BgmTvClient;
import com.ligg.common.constants.Constants;
import com.ligg.common.entity.UserEntity;
import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.response.AccessToken;
import com.ligg.common.response.FlowTokenVo;
import com.ligg.common.statuenum.Platform;
import com.ligg.common.vo.BangumiUserinfoVO;
import com.ligg.flowclient.mapper.UserMapper;
import com.ligg.flowclient.mapper.UserOauthMapper;
import com.ligg.flowclient.module.vo.BangumiBindVo;
import com.ligg.flowclient.service.JwtTokenService;
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
    private final UserMapper userMapper;
    private final BgmTvClient bgmTvClient;
    private final BangumiClient bangumiClient;
    private final JwtTokenService jwtTokenService;

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

        BangumiOAuthContext context = exchangeBangumiOAuth(code);
        Long platformUid = context.platformUid();

        UserOauthEntity boundToOther = findByPlatformUid(platformUid);
        if (boundToOther != null && !userId.equals(boundToOther.getUserId())) {
            throw new IllegalArgumentException("该 Bangumi 账号已被其他用户绑定");
        }

        UserOauthEntity existing = findByUserAndPlatform(userId);
        if (existing != null && !platformUid.equals(existing.getPlatformUid())) {
            throw new IllegalArgumentException("当前账号已绑定其他 Bangumi 账号");
        }

        upsertUserOauth(existing, userId, context);
        return toBindVo(context.me());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowTokenVo loginBangumi(String code, Platform platform) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("授权码无效");
        }

        BangumiOAuthContext context = exchangeBangumiOAuth(code);
        Long platformUid = context.platformUid();
        UserOauthEntity oauth = findByPlatformUid(platformUid);

        if (oauth != null) {
            upsertUserOauth(oauth, oauth.getUserId(), context);
            UserEntity user = requireUser(oauth.getUserId());
            return jwtTokenService.issueToken(user.getId(), user.getEmail(), platform);
        }

        UserEntity newUser = createUserFromBangumi(context.me());
        try {
            userMapper.insert(newUser);
        } catch (DuplicateKeyException e) {
            UserOauthEntity existingOauth = findByPlatformUid(platformUid);
            if (existingOauth != null) {
                upsertUserOauth(existingOauth, existingOauth.getUserId(), context);
                UserEntity user = requireUser(existingOauth.getUserId());
                return jwtTokenService.issueToken(user.getId(), user.getEmail(), platform);
            }
            throw new IllegalArgumentException("创建账号失败，请重试");
        }

        UserOauthEntity entity = new UserOauthEntity();
        entity.setUserId(newUser.getId());
        entity.setPlatform(Constants.BANGUMI_OAUTH_PLATFORM);
        entity.setPlatformUid(platformUid);
        entity.setAccessToken(context.token().getAccess_token());
        entity.setRefreshToken(context.token().getRefresh_token());
        entity.setExpireTime(context.expireTime());
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setStatus(STATUS_ACTIVE);
        try {
            userOauthMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            UserOauthEntity existingOauth = findByPlatformUid(platformUid);
            if (existingOauth != null) {
                upsertUserOauth(existingOauth, existingOauth.getUserId(), context);
                UserEntity user = requireUser(existingOauth.getUserId());
                return jwtTokenService.issueToken(user.getId(), user.getEmail(), platform);
            }
            throw new IllegalArgumentException("该 Bangumi 账号已被绑定");
        }

        return jwtTokenService.issueToken(newUser.getId(), null, platform);
    }

    private BangumiOAuthContext exchangeBangumiOAuth(String code) {
        AccessToken token = bgmTvClient.exchangeToken(code);
        BangumiUserinfoVO me = bangumiClient.getMe(token.getAccess_token());
        Long platformUid = me.id().longValue();
        long expireTime = Instant.now().getEpochSecond() + token.getExpires_in();
        return new BangumiOAuthContext(token, me, platformUid, expireTime);
    }

    private void upsertUserOauth(UserOauthEntity existing, Long userId, BangumiOAuthContext context) {
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setPlatformUid(context.platformUid());
            existing.setAccessToken(context.token().getAccess_token());
            existing.setRefreshToken(context.token().getRefresh_token());
            existing.setExpireTime(context.expireTime());
            existing.setUpdateTime(now);
            existing.setStatus(STATUS_ACTIVE);
            userOauthMapper.updateById(existing);
            return;
        }

        UserOauthEntity entity = new UserOauthEntity();
        entity.setUserId(userId);
        entity.setPlatform(Constants.BANGUMI_OAUTH_PLATFORM);
        entity.setPlatformUid(context.platformUid());
        entity.setAccessToken(context.token().getAccess_token());
        entity.setRefreshToken(context.token().getRefresh_token());
        entity.setExpireTime(context.expireTime());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setStatus(STATUS_ACTIVE);
        try {
            userOauthMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("该 Bangumi 账号已被绑定");
        }
    }

    private UserEntity createUserFromBangumi(BangumiUserinfoVO me) {
        UserEntity user = new UserEntity();
        user.setEmail(null);
        user.setPassword(null);
        user.setNickname(resolveNickname(me));
        user.setAvatar(resolveAvatar(me));
        user.setCreateTime(LocalDateTime.now());
        return user;
    }

    private UserEntity requireUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalStateException("绑定用户不存在");
        }
        return user;
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

    private static String resolveNickname(BangumiUserinfoVO me) {
        if (StringUtils.hasText(me.nickname())) {
            return me.nickname();
        }
        if (StringUtils.hasText(me.username())) {
            return me.username();
        }
        return "Bangumi用户";
    }

    private static String resolveAvatar(BangumiUserinfoVO me) {
        if (me.avatar() != null) {
            if (StringUtils.hasText(me.avatar().medium())) {
                return me.avatar().medium();
            }
            if (StringUtils.hasText(me.avatar().large())) {
                return me.avatar().large();
            }
        }
        return Constants.DEFAULT_USER_AVATAR_URL;
    }

    private static BangumiBindVo toBindVo(BangumiUserinfoVO me) {
        return new BangumiBindVo(
                true,
                me.id().longValue(),
                me.username(),
                me.nickname(),
                resolveAvatar(me)
        );
    }

    private record BangumiOAuthContext(
            AccessToken token,
            BangumiUserinfoVO me,
            Long platformUid,
            long expireTime
    ) {
    }
}
