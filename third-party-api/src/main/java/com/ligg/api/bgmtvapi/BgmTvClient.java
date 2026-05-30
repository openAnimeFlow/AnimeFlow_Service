package com.ligg.api.bgmtvapi;

import com.ligg.common.response.AccessToken;
import com.ligg.common.response.TokenVo;

/**
 * Bangumi 主站（bgm.tv）请求：页面抓取与 OAuth。
 */
public interface BgmTvClient {

    /**
     * 获取用户主页 HTML。
     *
     * @param username 用户名或用户 ID（如 {@code zxr}、{@code 1031261}）
     */
    String fetchUserPage(String username);

    /**
     * 授权码换取 AccessToken。
     */
    AccessToken exchangeToken(String code);

    /**
     * 刷新 AccessToken。
     */
    TokenVo refreshToken(String refreshToken);
}
