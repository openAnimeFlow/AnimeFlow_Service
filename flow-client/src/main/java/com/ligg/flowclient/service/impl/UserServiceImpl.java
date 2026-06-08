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
import com.ligg.common.response.FlowTokenVo;
import com.ligg.common.utils.PasswordUtils;
import com.ligg.flowclient.mapper.UserMapper;
import com.ligg.flowclient.module.dto.LoginDto;
import com.ligg.flowclient.module.dto.RegisterDto;
import com.ligg.flowclient.module.vo.UserVo;
import com.ligg.flowclient.service.JwtTokenService;
import com.ligg.flowclient.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtTokenService jwtTokenService;

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
        newUser.setCreateTime(LocalDateTime.now());

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
    public UserVo getUserInfo(String accessToken) {
        Long userId = jwtTokenService.validateAccessToken(accessToken);
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new LoginExpiredException();
        }
        return new UserVo(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatar(),
                user.getCreateTime()
        );
    }
}
