package com.ligg.flowclient.service;

import com.ligg.flowclient.module.vo.BangumiBindVo;

public interface UserOauthService {

    /**
     * 查询当前用户 Bangumi 绑定状态。
     */
    BangumiBindVo getBangumiBind(Long userId);

    /**
     * 使用 Bangumi OAuth 授权码绑定账号。
     */
    BangumiBindVo bindBangumi(Long userId, String code);
}
