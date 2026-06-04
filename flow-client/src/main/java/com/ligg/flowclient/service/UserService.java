package com.ligg.flowclient.service;

import com.ligg.flowclient.module.dto.RegisterDto;

public interface UserService {

    /**
     * 注册账户
     */
    void register(RegisterDto registerDto);
}
