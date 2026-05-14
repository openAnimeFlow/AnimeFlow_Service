/**
 * @author Ligg
 * @date 2026/5/5 11:08
 */
package com.ligg.api.bangumiapi;

import com.ligg.common.constants.ApiConstant;
import com.ligg.common.exception.BangumiUpstreamException;
import com.ligg.common.exception.LoginExpiredException;
import com.ligg.common.vo.BangumiUserinfoVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Service
public class BangumiClientImpl implements BangumiClient {

    private final Duration requestTimeout;
    private final WebClient webClient;

    public BangumiClientImpl(
            @Value("${anime-flow.bangumi.user-agent}") String bangumiUserAgent,
            @Value("${anime-flow.bangumi.request-timeout-seconds:30}") int requestTimeoutSeconds) {
        this.requestTimeout = Duration.ofSeconds(Math.max(5, requestTimeoutSeconds));
        HttpClient reactorHttpClient = HttpClient.create().responseTimeout(this.requestTimeout);
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(reactorHttpClient))
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
                    .block(requestTimeout);
        } catch (WebClientResponseException.Unauthorized e) {
            throw new LoginExpiredException(e);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new LoginExpiredException(e);
            }
            throw e;
        } catch (IllegalStateException e) {
            // reactor Mono.block(Duration) 超时
            if (e.getMessage() != null && e.getMessage().contains("Timeout")) {
                throw new BangumiUpstreamException("Bangumi 服务响应超时，请稍后重试", e);
            }
            throw e;
        } catch (WebClientRequestException e) {
            if (isTimeoutCause(e)) {
                throw new BangumiUpstreamException("Bangumi 服务响应超时，请稍后重试", e);
            }
            throw new BangumiUpstreamException("暂时无法连接 Bangumi，请检查网络后重试", e);
        }
    }

    private static boolean isTimeoutCause(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof TimeoutException) {
                return true;
            }
            String msg = c.getMessage();
            if (msg != null && (msg.contains("timeout") || msg.contains("Timeout"))) {
                return true;
            }
        }
        return false;
    }
}
