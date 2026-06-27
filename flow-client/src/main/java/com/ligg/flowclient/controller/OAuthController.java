/**
 * @author Ligg
 * @date 2025/8/7
 **/
package com.ligg.flowclient.controller;

import com.ligg.api.bgmtvapi.BgmTvClient;
import com.ligg.common.constants.Constants;
import com.ligg.common.response.*;
import com.ligg.common.statuenum.Platform;
import com.ligg.common.statuenum.ResponseCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/oauth")
public class OAuthController {

    private final BgmTvClient bgmTvClient;
    private final RedisTemplate<String, Object> redisTemplate;

    @PostMapping("/token")
    public Result<AccessToken> accessToken(String code) {
        if (!StringUtils.hasText(code)) {
            return Result.error(ResponseCode.PARAM_ERROR);
        }
        log.info("接收到code: {}", code);
        if (code.length() > 100) {
            return Result.error(ResponseCode.PARAM_ERROR);
        }

        AccessToken token = bgmTvClient.exchangeToken(code);
        log.info("返回的token: {}", token);
        return Result.success(ResponseCode.SUCCESS, token);
    }

    /**
     * 申请session
     */
    @GetMapping("/session")
    public Result<SessionVo> session(
            Platform platform,
            @RequestParam(required = false, defaultValue = "false") Boolean bindMode) {
        String sessionId = "animeFlow" + new Random().nextInt(100000);
        int expiresIn = 60;
        SessionVo sessionVo = new SessionVo();
        sessionVo.setSessionId(sessionId);
        sessionVo.setExpiresIn(expiresIn);

        SessionDto sessionDto = new SessionDto();
        sessionDto.setSessionId(sessionId);
        sessionDto.setExpiresIn(expiresIn);
        sessionDto.setPlatform(platform);
        sessionDto.setBindMode(bindMode);
        redisTemplate.opsForValue().set(Constants.SESSION_KEY + ':' + sessionId, sessionDto, expiresIn, TimeUnit.SECONDS);
        return Result.success(ResponseCode.SUCCESS, sessionVo);
    }

    /**
     * 回调
     */
    @GetMapping("/callback")
    public void callback(String code, String state, HttpServletResponse response) throws Exception {
        try {
            if (!StringUtils.hasText(code)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            if (code.length() > 100) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            SessionDto sessionDto = (SessionDto) redisTemplate.opsForValue().get(Constants.SESSION_KEY + ':' + state);
            if (sessionDto == null) {
                response.sendRedirect("/api/no_session");
                return;
            }

            redisTemplate.delete(Constants.SESSION_KEY + ':' + state);
            Platform platform = sessionDto.getPlatform();
            if (platform == Platform.ANDROID || platform == Platform.IOS) {
                StringBuilder redirectUrl = new StringBuilder(Constants.MOBILE_CALLBACK_URL)
                        .append("?code=").append(URLEncoder.encode(code, StandardCharsets.UTF_8))
                        .append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
                if (Boolean.TRUE.equals(sessionDto.getBindMode())) {
                    redirectUrl.append('&')
                            .append(Constants.OAUTH_CALLBACK_PURPOSE_PARAM)
                            .append('=')
                            .append(Constants.OAUTH_CALLBACK_PURPOSE_BIND);
                }
                response.sendRedirect(redirectUrl.toString());
            } else if (Boolean.TRUE.equals(sessionDto.getBindMode())) {
                redisTemplate.opsForValue().set(
                        Constants.BIND_CODE_KEY + ':' + state,
                        code,
                        sessionDto.getExpiresIn(),
                        TimeUnit.SECONDS);
                response.sendRedirect("/api/oauth-success?state=" + state);
            } else {
                AccessToken token = bgmTvClient.exchangeToken(code);
                redisTemplate.opsForValue().set(Constants.AUTO_TOKEN_KEY + ':' + state, token, token.getExpires_in(), TimeUnit.SECONDS);
                response.sendRedirect("/api/oauth-success?state=" + state);
            }
        } catch (Exception e) {
            log.error("回调异常", e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    /**
     * 获取缓存的token
     */
    @GetMapping("/token")
    public Result<AccessToken> getToken(String sessionId) {
        log.info("获取的sessionId: {}", sessionId);
        AccessToken token = (AccessToken) redisTemplate.opsForValue().get(Constants.AUTO_TOKEN_KEY + ':' + sessionId);
        if (token == null) {
            return Result.error(ResponseCode.PARAM_ERROR);
        }
        redisTemplate.delete(Constants.AUTO_TOKEN_KEY + ':' + sessionId);
        return Result.success(ResponseCode.SUCCESS, token);
    }

    /**
     * 桌面端绑定模式：获取缓存的 OAuth 授权码
     */
    @GetMapping("/bind-code")
    public Result<String> getBindCode(String sessionId) {
        log.info("获取绑定授权码 sessionId: {}", sessionId);
        Object code = redisTemplate.opsForValue().get(Constants.BIND_CODE_KEY + ':' + sessionId);
        if (!(code instanceof String bindCode) || !StringUtils.hasText(bindCode)) {
            return Result.error(ResponseCode.PARAM_ERROR);
        }
        redisTemplate.delete(Constants.BIND_CODE_KEY + ':' + sessionId);
        return Result.success(ResponseCode.SUCCESS, bindCode);
    }

    /**
     * 刷新bgm token
     */
    @PostMapping("/refresh")
    public Result<TokenVo> refreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return Result.error(ResponseCode.PARAM_ERROR);
        }
        TokenVo token = bgmTvClient.refreshToken(refreshToken);
        return Result.success(ResponseCode.SUCCESS, token);
    }

}
