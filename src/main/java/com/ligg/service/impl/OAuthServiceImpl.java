package com.ligg.service.impl;

import com.ligg.module.constants.Constants;
import com.ligg.module.response.AccessToken;
import com.ligg.service.OAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * @Author Ligg
 * @Time 2025/8/7
 **/
@Service
public class OAuthServiceImpl implements OAuthService {

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
        RestClient restClient = RestClient.builder().build();

        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", Constants.BANGUMI_GRANT_TYPE);
        formData.add("client_id", CLIENT_ID);
        formData.add("client_secret", CLIENT_SECRET);
        formData.add("code", code);
        formData.add("redirect_uri", REDIRECT_URI);
        return restClient.post()
                .uri(Constants.BANGUMI_Token_API)
                .body(formData)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .retrieve().body(AccessToken.class);
    }

    /**
     * 刷新token
     *
     * @return AccessToken
     */
    @Override
    public AccessToken refreshToken(String refreshToken) {
        RestClient restClient = RestClient.builder().build();

        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", CLIENT_ID);
        formData.add("client_secret", CLIENT_SECRET);
        formData.add("refresh_token", refreshToken);
        formData.add("redirect_uri", REDIRECT_URI);
        return restClient.post()
                .uri(Constants.BANGUMI_Token_API)
                .body(formData)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .retrieve().body(AccessToken.class);
    }
}
