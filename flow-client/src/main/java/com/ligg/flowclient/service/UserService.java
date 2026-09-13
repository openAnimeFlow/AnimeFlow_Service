package com.ligg.flowclient.service;

import com.ligg.common.response.FlowTokenVo;
import com.ligg.flowclient.module.dto.BindEmailDto;
import com.ligg.flowclient.module.dto.ChangePasswordDto;
import com.ligg.flowclient.module.dto.ForgotPasswordDto;
import com.ligg.flowclient.module.dto.LoginDto;
import com.ligg.flowclient.module.dto.RegisterDto;
import com.ligg.flowclient.module.dto.UpdateUserDto;
import com.ligg.flowclient.module.vo.FlowUserVo;
import org.springframework.web.multipart.MultipartFile;

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
    FlowUserVo getUserInfo(String accessToken);

    /**
     * 更新当前登录用户资料。
     */
    FlowUserVo updateUserInfo(String accessToken, UpdateUserDto updateUserDto);

    /**
     * 上传用户头像到对象存储。
     *
     * @param file        图片文件（支持 JPEG / PNG / WebP / GIF）
     * @return 上传后的头像 URL
     */
    String uploadAvatar(MultipartFile file);

    /**
     * 为当前账号绑定邮箱并设置登录密码。
     */
    FlowUserVo bindEmail(Long userId, BindEmailDto bindEmailDto);

    /**
     * 通过邮箱验证码重置登录密码，每个邮箱每天仅可重置一次。
     */
    void resetPassword(ForgotPasswordDto forgotPasswordDto);

    /**
     * 修改密码
     */
    void changePassword(String accessToken, ChangePasswordDto changePasswordDto);
}
