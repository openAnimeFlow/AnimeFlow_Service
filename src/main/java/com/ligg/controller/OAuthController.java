package com.ligg.controller;

import com.ligg.module.constants.Constants;
import com.ligg.module.response.AccessToken;
import com.ligg.module.response.Result;
import com.ligg.module.response.SessionDto;
import com.ligg.module.response.SessionVo;
import com.ligg.module.statuenum.Platform;
import com.ligg.module.statuenum.ResponseCode;
import com.ligg.service.OAuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @Author Ligg
 * @Time 2025/8/7
 **/
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/oauth")
public class OAuthController {

    @Value("${bangumi.client_id}")
    private String CLIENT_ID;
    @Value("${bangumi.client_secret}")
    private String CLIENT_SECRET;
    @Value("${bangumi.redirect_uri}")
    private String REDIRECT_URI;

    private final OAuthService oAuthService;
    private final RedisTemplate<String, Object> redisTemplate;

    @ResponseBody
    @PostMapping("/token")
    public Result<AccessToken> accessToken(String code) {
        if (!StringUtils.hasText(code)) {
            return Result.error(ResponseCode.PARAM_ERROR);
        }
        log.info("接收到code: {}", code);
        if (code.length() > 100) {
            return Result.error(ResponseCode.PARAM_ERROR);
        }

        AccessToken token = oAuthService.getToken(code);
        log.info("返回的token: {}", token);
        return Result.success(ResponseCode.SUCCESS, token);
    }

    /**
     * 申请session
     */
    @ResponseBody
    @GetMapping("/session")
    public Result<SessionVo> session(Platform platform) {
        String sessionId = "animeFlow" + new Random().nextInt(100000);
        long expiresIn = System.currentTimeMillis() + 1000 * 60 * 10;
        SessionVo sessionVo = new SessionVo();
        sessionVo.setSessionId(sessionId);
        sessionVo.setExpiresIn(expiresIn);

        SessionDto sessionDto = new SessionDto();
        sessionDto.setSessionId(sessionId);
        sessionDto.setExpiresIn((int) expiresIn);
        sessionDto.setPlatform(platform);
        redisTemplate.opsForValue().set(Constants.SESSION_KEY + ':' + sessionId, sessionDto, expiresIn, TimeUnit.MILLISECONDS);
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
            log.info("接收到code: {}", code);
            if (code.length() > 100) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            SessionDto sessionDto = (SessionDto) redisTemplate.opsForValue().get(Constants.SESSION_KEY + ':' + state);
            if (sessionDto == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "本次授权已过期");
                return;
            }
            redisTemplate.delete(Constants.SESSION_KEY + ':' + state);
            Platform platform = sessionDto.getPlatform();
            if (platform == Platform.ANDROID || platform == Platform.IOS) {

                String redirectUrl = Constants.MOBILE_CALLBACK_URL + "?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) + "&state=" + state;
                response.sendRedirect(redirectUrl);
            } else {
                response.sendRedirect("返回网页");
            }
        } catch (Exception e) {
            log.error("回调异常", e);
        }
    }
}
