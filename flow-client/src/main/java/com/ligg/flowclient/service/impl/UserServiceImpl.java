/**
 * @author Ligg
 * @date 2026/6/5 04:53
 */
package com.ligg.flowclient.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ligg.common.constants.Constants;
import com.ligg.common.entity.UserEntity;
import com.ligg.common.exception.AuthenticationFailedException;
import com.ligg.common.exception.LoginExpiredException;
import com.ligg.common.exception.RateLimitExceededException;
import com.ligg.common.response.FlowTokenVo;
import com.ligg.common.utils.PasswordUtils;
import com.ligg.flowclient.mapper.UserMapper;
import com.ligg.flowclient.module.dto.BindEmailDto;
import com.ligg.flowclient.module.dto.ForgotPasswordDto;
import com.ligg.flowclient.module.dto.LoginDto;
import com.ligg.flowclient.module.dto.RegisterDto;
import com.ligg.flowclient.module.dto.UpdateUserDto;
import com.ligg.flowclient.module.dto.UserProfileRow;
import com.ligg.flowclient.module.vo.UserCollectionCountsVo;
import com.ligg.flowclient.module.vo.FlowUserVo;
import com.ligg.flowclient.service.EmailService;
import com.ligg.flowclient.service.JwtTokenService;
import com.ligg.flowclient.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final int DAILY_LIMIT_SECONDS = 86_400;

    private final UserMapper userMapper;
    private final JwtTokenService jwtTokenService;
    private final EmailService emailService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 注册账户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDto registerDto) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getEmail, registerDto.getEmail());

        if (userMapper.selectOne(wrapper) != null) {
            throw new IllegalArgumentException("该邮箱已被注册");
        }

        UserEntity newUser = new UserEntity();
        newUser.setEmail(registerDto.getEmail());
        newUser.setPassword(PasswordUtils.hash(registerDto.getPassword()));
        newUser.setNickname(registerDto.getEmail().split("@")[0]);
        newUser.setAvatar(Constants.DEFAULT_USER_AVATAR_URL);
        newUser.setCreateTime(Instant.now().getEpochSecond());

        try {
            userMapper.insert(newUser);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("该邮箱已被注册");
        }
    }

    @Override
    public FlowTokenVo login(LoginDto loginDto) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getEmail, loginDto.getEmail());

        UserEntity user = userMapper.selectOne(wrapper);
        if (user == null || !PasswordUtils.verify(loginDto.getPassword(), user.getPassword())) {
            throw new AuthenticationFailedException();
        }

        return jwtTokenService.issueToken(user.getId(), user.getEmail(), loginDto.getPlatform());
    }

    @Override
    public FlowUserVo getUserInfo(String accessToken) {
        Long userId = jwtTokenService.validateAccessToken(accessToken);
        return loadUserVo(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowUserVo updateUserInfo(String accessToken, UpdateUserDto updateUserDto) {
        if (!updateUserDto.hasUpdateField()) {
            throw new IllegalArgumentException("至少需要更新一个用户信息字段");
        }
        Long userId = jwtTokenService.validateAccessToken(accessToken);
        checkUserInfoUpdateDailyLimit(userId);
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new LoginExpiredException();
        }
        if (StringUtils.hasText(updateUserDto.getNickname())) {
            user.setNickname(updateUserDto.getNickname().trim());
        }
        if (StringUtils.hasText(updateUserDto.getAvatar())) {
            user.setAvatar(updateUserDto.getAvatar().trim());
        }
        userMapper.updateById(user);
        markUserInfoUpdateDaily(userId);
        return loadUserVo(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowUserVo bindEmail(Long userId, BindEmailDto bindEmailDto) {
        emailService.verifyEmailCode(bindEmailDto.getEmail(), bindEmailDto.getEmailCaptcha());

        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new LoginExpiredException();
        }
        if (StringUtils.hasText(user.getEmail())) {
            throw new IllegalArgumentException("当前账号已绑定邮箱");
        }

        LambdaQueryWrapper<UserEntity> emailWrapper = new LambdaQueryWrapper<>();
        emailWrapper.eq(UserEntity::getEmail, bindEmailDto.getEmail());
        if (userMapper.selectOne(emailWrapper) != null) {
            throw new IllegalArgumentException("该邮箱已被注册");
        }

        user.setEmail(bindEmailDto.getEmail());
        user.setPassword(PasswordUtils.hash(bindEmailDto.getPassword()));
        try {
            userMapper.updateById(user);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("该邮箱已被注册");
        }

        jwtTokenService.updateUserEmail(userId, bindEmailDto.getEmail());

        return loadUserVo(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ForgotPasswordDto forgotPasswordDto) {
        final String email = forgotPasswordDto.getEmail();
        checkPasswordResetDailyLimit(email);

        emailService.verifyEmailCode(email, forgotPasswordDto.getEmailCaptcha());

        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getEmail, email);
        UserEntity user = userMapper.selectOne(wrapper);
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            throw new IllegalArgumentException("该邮箱未注册");
        }

        user.setPassword(PasswordUtils.hash(forgotPasswordDto.getPassword()));
        userMapper.updateById(user);
        markPasswordResetDaily(email);
    }

    private void checkPasswordResetDailyLimit(String email) {
        String key = Constants.PASSWORD_RESET_DAILY_KEY + ':' + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            throw new RateLimitExceededException("今日已重置过密码，请明天再试");
        }
    }

    private void markPasswordResetDaily(String email) {
        String key = Constants.PASSWORD_RESET_DAILY_KEY + ':' + email;
        redisTemplate.opsForValue().set(key, "1", DAILY_LIMIT_SECONDS, TimeUnit.SECONDS);
    }

    private void checkUserInfoUpdateDailyLimit(Long userId) {
        String key = Constants.USER_INFO_UPDATE_DAILY_KEY + ':' + userId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            throw new IllegalArgumentException("今日已更新过用户资料，请明天再试");
        }
    }

    private void markUserInfoUpdateDaily(Long userId) {
        String key = Constants.USER_INFO_UPDATE_DAILY_KEY + ':' + userId;
        redisTemplate.opsForValue().set(key, "1", DAILY_LIMIT_SECONDS, TimeUnit.SECONDS);
    }

    private FlowUserVo loadUserVo(Long userId) {
        UserProfileRow row = userMapper.selectProfileById(userId);
        if (row == null) {
            throw new LoginExpiredException();
        }
        return toUserVo(row);
    }

    private static FlowUserVo toUserVo(UserProfileRow row) {
        UserCollectionCountsVo collectionCounts = new UserCollectionCountsVo(
                row.getPlanToWatch(),
                row.getWatched(),
                row.getWatching(),
                row.getOnHold(),
                row.getAbandoned()
        );
        return new FlowUserVo(
                row.getId(),
                row.getEmail(),
                row.getNickname(),
                row.getAvatar(),
                row.getCreateTime(),
                collectionCounts
        );
    }
}
