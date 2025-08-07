package com.ligg.service.impl;

import com.ligg.module.constants.BangumiConstants;
import com.ligg.module.response.AccessToken;
import com.ligg.service.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * @Author Ligg
 * @Time 2025/8/7
 **/
@Service
public class TokenServiceImpl implements TokenService {

    @Value("${bangumi.client_id}")
    private String CLIENT_ID;
    @Value("${bangumi.client_secret}")
    private String CLIENT_SECRET;
    @Value("${bangumi.redirect_uri}")
    private String REDIRECT_URI;


    /**
     * 发送请求获取token
     *
     * @return AccessToken
     */
    @Override
    public AccessToken getToken(String code) {
        RestTemplate restTemplate = new RestTemplate();

        //设置请求头
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setCacheControl(BangumiConstants.BANGUMI_HEADERS);

        //设置请求参数
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("grant_type", BangumiConstants.BANGUMI_GRANT_TYPE);
        body.add("client_id", CLIENT_ID);
        body.add("client_secret", CLIENT_SECRET);
        body.add("code", code);
        body.add("redirect_uri", REDIRECT_URI);

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, httpHeaders);

        //发送请求
        ResponseEntity<AccessToken> response = restTemplate.postForEntity(BangumiConstants.BANGUMI_Token_API, request, AccessToken.class);
        HttpStatusCode statusCode = response.getStatusCode();

        if (statusCode.value() != 200) {
            throw new RuntimeException("请求失败");
        }
        return response.getBody();
    }
}
