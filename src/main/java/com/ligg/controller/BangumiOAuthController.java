package com.ligg.controller;

import com.ligg.module.response.AccessToken;
import com.ligg.module.response.Result;
import com.ligg.module.statuenum.ResponseCode;
import com.ligg.service.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author Ligg
 * @Time 2025/8/7
 **/
@Slf4j
@RestController
@RequestMapping("/oauth")
public class BangumiOAuthController {

    @Autowired
    private TokenService tokenService;

    @PostMapping("access_token")
    public Result<AccessToken> accessToken(String code) {
        log.info("接收到code: {}", code);
        //校验字符串的长度
        if (code.length() > 100){
            return Result.error(ResponseCode.PARAM_ERROR);
        }
        AccessToken token = tokenService.getToken(code);
        log.info("返回的token: {}", token);
        return Result.success(ResponseCode.SUCCESS,token);
    }
}
