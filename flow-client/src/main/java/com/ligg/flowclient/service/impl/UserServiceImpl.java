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
import com.ligg.common.storage.ObjectStorageService;
import com.ligg.common.utils.ImageValidator;
import com.ligg.common.utils.PasswordUtils;
import com.ligg.flowclient.mapper.UserMapper;
import com.ligg.flowclient.module.dto.*;
import com.ligg.flowclient.module.vo.FlowUserVo;
import com.ligg.flowclient.module.vo.UserCollectionCountsVo;
import com.ligg.flowclient.service.EmailService;
import com.ligg.flowclient.service.JwtTokenService;
import com.ligg.flowclient.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final int DAILY_LIMIT_SECONDS = 86_400;
    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private final UserMapper userMapper;
    private final JwtTokenService jwtTokenService;
    private final EmailService emailService;
    private final ObjectStorageService objectStorageService;
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
        if (updateUserDto.getBackgroundId() != null) {
            user.setBackgroundId(updateUserDto.getBackgroundId());
        }
        userMapper.updateById(user);
        markUserInfoUpdateDaily(userId);
        return loadUserVo(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowUserVo uploadAvatar(String accessToken, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("头像文件不能为空");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new IllegalArgumentException("头像文件大小不能超过 2MB");
        }

        // Content-Type 初筛（客户端可伪造，仅做快速拦截）
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_AVATAR_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("仅支持 JPEG、PNG、WebP、GIF 格式的图片");
        }

        // 文件扩展名白名单
        String ext = ImageValidator.validateExtension(file.getOriginalFilename());
        if (ext == null) {
            throw new IllegalArgumentException("文件扩展名不合法，仅支持 jpg/png/webp/gif");
        }

        // 魔术字节 + 解码验证 + 像素上限 + EXIF 剥离
        ImageValidator.SanitizeResult sanitized;
        try {
            sanitized = ImageValidator.sanitize(file.getBytes());
        } catch (IOException e) {
            log.error("图片校验/清洗失败", e);
            throw new IllegalStateException("头像上传失败，请稍后重试");
        }
        if (sanitized == null) {
            throw new IllegalArgumentException("文件内容不是合法的图片格式");
        }

        Long userId = jwtTokenService.validateAccessToken(accessToken);
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new LoginExpiredException();
        }

        String key = "avatars/" + userId + "/" + UUID.randomUUID() + "." + ext;
        byte[] cleanBytes = sanitized.bytes();

        String avatarUrl = objectStorageService.upload(
                key,
                new ByteArrayInputStream(cleanBytes),
                cleanBytes.length,
                sanitized.mimeType());

        deleteOldAvatar(user.getAvatar());

        user.setAvatar(avatarUrl);
        userMapper.updateById(user);
        return loadUserVo(userId);
    }

    private void deleteOldAvatar(String oldAvatarUrl) {
        if (!StringUtils.hasText(oldAvatarUrl) || oldAvatarUrl.equals(Constants.DEFAULT_USER_AVATAR_URL)) {
            return;
        }
        try {
            String publicUrlBase = objectStorageService.getPublicUrl("");
            if (oldAvatarUrl.startsWith(publicUrlBase)) {
                String oldKey = oldAvatarUrl.substring(publicUrlBase.length());
                objectStorageService.delete(oldKey);
            }
        } catch (Exception e) {
            log.warn("删除旧头像失败，已忽略: url={}", oldAvatarUrl, e);
        }
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
        if (redisTemplate.hasKey(key)) {
            throw new RateLimitExceededException("今日已重置过密码，请明天再试");
        }
    }

    private void markPasswordResetDaily(String email) {
        String key = Constants.PASSWORD_RESET_DAILY_KEY + ':' + email;
        redisTemplate.opsForValue().set(key, "1", DAILY_LIMIT_SECONDS, TimeUnit.SECONDS);
    }

    private void checkUserInfoUpdateDailyLimit(Long userId) {
        String key = Constants.USER_INFO_UPDATE_DAILY_KEY + ':' + userId;
        if (redisTemplate.hasKey(key)) {
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
