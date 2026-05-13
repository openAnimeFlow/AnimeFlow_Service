/**
 * @author Ligg
 * @date 2026/5/5 11:08
 */
package com.ligg.api.bangumiapi;

import com.ligg.common.constants.ApiConstant;
import com.ligg.common.exception.LoginExpiredException;
import com.ligg.common.vo.BangumiUserinfoVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

@Service
public class BangumiClientImpl implements BangumiClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final WebClient webClient;

    public BangumiClientImpl(@Value("${anime-flow.bangumi.user-agent}") String bangumiUserAgent) {
        this.webClient = WebClient.builder()
                .baseUrl(ApiConstant.BANGUMI_NEXT_API_BASE_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, bangumiUserAgent)
                .build();
    }

    /**
     * 获取当前用户信息
     */
    @Override
    public BangumiUserinfoVO getMe(String accessToken) {
        try {
            return webClient.get()
                    .uri(ApiConstant.ME)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(BangumiUserinfoVO.class)
                    .block(REQUEST_TIMEOUT);
        } catch (WebClientResponseException.Unauthorized e) {
            throw new LoginExpiredException(e);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new LoginExpiredException(e);
            }
            throw e;
        }
    }
}
