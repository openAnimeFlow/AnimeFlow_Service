package com.ligg.flowclient.service;

import com.ligg.common.response.FlowTokenVo;
import com.ligg.flowclient.module.dto.LoginDto;
import com.ligg.flowclient.module.dto.RegisterDto;
import com.ligg.flowclient.module.vo.UserVo;

public interface UserService {

    /**
     * 注册账户
     */
    void register(RegisterDto registerDto);

    /**
     * 邮箱密码登录，成功返回 JWT。
     */
    FlowTokenVo login(LoginDto loginDto);

    /**
     * 根据 access_token 获取当前登录用户信息。
     */
    UserVo getUserInfo(String accessToken);
}
