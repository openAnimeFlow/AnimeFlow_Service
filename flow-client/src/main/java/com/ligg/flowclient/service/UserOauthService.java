package com.ligg.flowclient.service;

import com.ligg.common.response.FlowTokenVo;
import com.ligg.common.statuenum.Platform;
import com.ligg.flowclient.module.vo.BangumiBindVo;

public interface UserOauthService {

    /**
     * 查询当前用户 Bangumi 绑定状态。
     */
    BangumiBindVo getBangumiBind(Long userId);

    /**
     * 使用 Bangumi OAuth 授权码绑定账号（需已登录 AnimeFlow）。
     */
    BangumiBindVo bindBangumi(Long userId, String code);

    /**
     * Bangumi 第三方授权登录：已绑定则直接登录，未绑定则创建本地账号（邮箱/密码为空）并登录。
     */
    FlowTokenVo loginBangumi(String code, Platform platform);
}
