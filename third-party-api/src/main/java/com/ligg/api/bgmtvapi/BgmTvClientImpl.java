package com.ligg.api.bgmtvapi;

import com.ligg.api.config.BangumiProperties;
import com.ligg.api.config.WebClientConfig;
import com.ligg.common.apipath.BgmTvApiPath;
import com.ligg.common.constants.Constants;
import com.ligg.common.exception.BangumiUpstreamException;
import com.ligg.common.response.AccessToken;
import com.ligg.common.response.TokenVo;
import com.ligg.common.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import io.netty.handler.timeout.ReadTimeoutException;

@Slf4j
@Service
public class BgmTvClientImpl implements BgmTvClient {

    private final Duration requestTimeout;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final WebClient bgmTvClient;

    public BgmTvClientImpl(
            BangumiProperties bangumiProperties,
            @Qualifier(WebClientConfig.BGM_TV_WEB_CLIENT) WebClient bgmTvClient) {
        this.clientId = bangumiProperties.getClientId();
        this.clientSecret = bangumiProperties.getClientSecret();
        this.redirectUri = bangumiProperties.getRedirectUri();
        this.requestTimeout = Duration.ofSeconds(Math.max(5, bangumiProperties.getRequestTimeoutSeconds()));
        this.bgmTvClient = bgmTvClient;
    }

    @Override
    public String fetchUserPage(String username) {
        log.info("获取 Bangumi 用户主页 username={}", username);
        return blockBgmTv(bgmTvClient.get()
                .uri(BgmTvApiPath.USER_PAGE, username)
                .header(HttpHeaders.USER_AGENT, Utils.getRandomUserAgent())
                .retrieve()
                .bodyToMono(String.class), "获取用户主页失败");
    }

    @Override
    public AccessToken exchangeToken(String code) {
        log.info("Bangumi OAuth 换取 token");
        MultiValueMap<String, String> form = oauthForm(Constants.BANGUMI_GRANT_TYPE);
        form.add("code", code);
        return blockBgmTv(postTokenForm(form).bodyToMono(AccessToken.class), "换取 token 失败");
    }

    @Override
    public TokenVo refreshToken(String refreshToken) {
        log.info("Bangumi OAuth 刷新 token");
        MultiValueMap<String, String> form = oauthForm("refresh_token");
        form.add("refresh_token", refreshToken);
        return blockBgmTv(postTokenForm(form).bodyToMono(AccessToken.class), "刷新 token 失败");
    }

    private MultiValueMap<String, String> oauthForm(String grantType) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", grantType);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        return form;
    }

    private WebClient.ResponseSpec postTokenForm(MultiValueMap<String, String> form) {
        return bgmTvClient.post()
                .uri(BgmTvApiPath.TOKEN_API)
                .header(HttpHeaders.USER_AGENT, Utils.getRandomUserAgent())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve();
    }

    private <T> T blockBgmTv(Mono<T> mono, String failureMessage) {
        try {
            return mono.timeout(requestTimeout).block();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new BangumiUpstreamException(failureMessage + ": 资源不存在", e);
            }
            if (e.getStatusCode().is4xxClientError()) {
                String responseBody = e.getResponseBodyAsString();
                log.warn("bgm.tv 请求被拒绝 status={} body={}", e.getStatusCode().value(), responseBody);
                throw new BangumiUpstreamException(failureMessage + ": " + responseBody, e);
            }
            throw e;
        } catch (WebClientRequestException e) {
            throw toBangumiUpstreamException(e);
        } catch (RuntimeException e) {
            if (isTimeoutCause(e)) {
                throw new BangumiUpstreamException("bgm.tv 响应超时，请稍后重试", e);
            }
            throw e;
        }
    }

    private static BangumiUpstreamException toBangumiUpstreamException(WebClientRequestException e) {
        if (isTimeoutCause(e)) {
            return new BangumiUpstreamException("bgm.tv 响应超时，请稍后重试", e);
        }
        return new BangumiUpstreamException("暂时无法连接 bgm.tv，请检查网络后重试", e);
    }

    private static boolean isTimeoutCause(Throwable e) {
        return hasCauseOfType(e, TimeoutException.class)
                || hasCauseOfType(e, SocketTimeoutException.class)
                || hasCauseOfType(e, ReadTimeoutException.class);
    }

    private static boolean hasCauseOfType(Throwable e, Class<? extends Throwable> type) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }
}
