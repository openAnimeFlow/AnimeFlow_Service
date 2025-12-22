package com.ligg.controller;

import com.ligg.module.constants.Constants;
import com.ligg.module.response.AccessToken;
import com.ligg.module.response.Result;
import com.ligg.module.response.SessionDto;
import com.ligg.module.response.SessionVo;
import com.ligg.module.statuenum.Platform;
import com.ligg.module.statuenum.ResponseCode;
import com.ligg.service.OAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @Author Ligg
 * @Time 2025/8/7
 **/
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/oauth")
public class OAuthController {

    private final OAuthService oAuthService;
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

        AccessToken token = oAuthService.getToken(code);
        log.info("返回的token: {}", token);
        return Result.success(ResponseCode.SUCCESS, token);
    }

    /**
     * 申请session
     */
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
    public Result<String> callback(String code, String state) {
        if (!StringUtils.hasText(code)) {
            return Result.error(ResponseCode.PARAM_ERROR);
        }
        log.info("接收到code: {}", code);
        if (code.length() > 100) {
            return Result.error(ResponseCode.PARAM_ERROR);
        }

        SessionDto sessionDto = (SessionDto) redisTemplate.opsForValue().get(Constants.SESSION_KEY + ':' + state);
        if (sessionDto == null) {
            return Result.error(ResponseCode.PARAM_ERROR, "本次授权已过期");
        }
        redisTemplate.delete(Constants.SESSION_KEY + ':' + state);
        Platform platform = sessionDto.getPlatform();
        if (platform == Platform.ANDROID || platform == Platform.IOS) {
            return Result.success(ResponseCode.SUCCESS,"移动端");
        } else {
            return Result.success(ResponseCode.SUCCESS,"桌面端");
        }
    }
}
