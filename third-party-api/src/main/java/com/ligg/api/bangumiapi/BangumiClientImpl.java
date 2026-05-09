/**
 * @author Ligg
 * @date 2026/5/5 11:08
 */
package com.ligg.api.bangumiapi;

import com.ligg.common.constants.ApiConstant;
import com.ligg.common.exception.BangumiLoginExpiredException;
import com.ligg.common.vo.BangumiUserinfoVO;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

@Service
public class BangumiClientImpl implements BangumiClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final WebClient webClient = WebClient.builder()
            .baseUrl(ApiConstant.BANGUMI_NEXT_API_BASE_URL)
            .build();

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
            throw new BangumiLoginExpiredException(e);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new BangumiLoginExpiredException(e);
            }
            throw e;
        }
    }
}
