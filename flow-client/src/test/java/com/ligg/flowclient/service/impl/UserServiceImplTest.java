package com.ligg.flowclient.service.impl;

import com.ligg.common.entity.UserEntity;
import com.ligg.common.exception.UpdateRateLimitException;
import com.ligg.common.storage.ObjectStorageService;
import com.ligg.common.utils.PasswordUtils;
import com.ligg.flowclient.mapper.UserMapper;
import com.ligg.flowclient.module.dto.ChangePasswordDto;
import com.ligg.flowclient.service.EmailService;
import com.ligg.flowclient.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private EmailService emailService;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Test
    void changePasswordUpdatesHashAndRevokesEverySession() {
        UserEntity user = new UserEntity();
        user.setId(42L);
        user.setPassword(PasswordUtils.hash("old-password"));
        ChangePasswordDto body = new ChangePasswordDto();
        body.setOldPassword("old-password");
        body.setNewPassword("new-password");
        when(jwtTokenService.validateAccessToken("access-token")).thenReturn(42L);
        when(userMapper.selectById(42L)).thenReturn(user);
        when(userMapper.updateById(user)).thenReturn(1);
        when(redisTemplate.getExpire(anyString(), eq(java.util.concurrent.TimeUnit.SECONDS))).thenReturn(0L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service().changePassword("access-token", body);

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).updateById(userCaptor.capture());
        assertTrue(PasswordUtils.verify("new-password", userCaptor.getValue().getPassword()));
        verify(jwtTokenService).revokeAllUserSessions(42L);
        verify(valueOperations).set(
                "animeflow:account:password-change:cooldown:42",
                "1",
                12 * 60 * 60,
                java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    void changePasswordRejectsRequestsDuringTwelveHourCooldown() {
        ChangePasswordDto body = new ChangePasswordDto();
        body.setOldPassword("old-password");
        body.setNewPassword("new-password");
        when(jwtTokenService.validateAccessToken("access-token")).thenReturn(42L);
        when(redisTemplate.getExpire(
                "animeflow:account:password-change:cooldown:42",
                java.util.concurrent.TimeUnit.SECONDS)).thenReturn(3600L);

        assertThrows(UpdateRateLimitException.class, () -> service().changePassword("access-token", body));

        verify(userMapper, never()).selectById(42L);
    }

    private UserServiceImpl service() {
        return new UserServiceImpl(userMapper, jwtTokenService, emailService, objectStorageService, redisTemplate);
    }
}
