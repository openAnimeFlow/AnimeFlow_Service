/**
 * @author Ligg
 * @date 2026/5/5 11:08
 */
package com.ligg.api.bangumiapi;

import com.ligg.common.apipath.BangumiApiPath;
import com.ligg.common.constants.ApiConstant;
import com.ligg.common.exception.BangumiUpstreamException;
import com.ligg.common.exception.LoginExpiredException;
import com.ligg.common.thirdparty.CalendarDto;
import com.ligg.common.thirdparty.TrendingSubjectsDto;
import com.ligg.common.vo.BangumiUserinfoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import io.netty.handler.timeout.ReadTimeoutException;

@Slf4j
@Service
public class BangumiClientImpl implements BangumiClient {

    private final Duration requestTimeout;
    private final WebClient bangumiNextClient;

    public BangumiClientImpl(
            @Value("${anime-flow.bangumi.user-agent}") String bangumiUserAgent,
            @Value("${anime-flow.bangumi.request-timeout-seconds:30}") int requestTimeoutSeconds) {
        this.requestTimeout = Duration.ofSeconds(Math.max(5, requestTimeoutSeconds));
        HttpClient reactorHttpClient = HttpClient.create().responseTimeout(this.requestTimeout);
        this.bangumiNextClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(reactorHttpClient))
                .baseUrl(BangumiApiPath.BANGUMI_NEXT_API_BASE_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, bangumiUserAgent)
                .build();
    }

    /**
     * 获取当前用户信息
     */
    @Override
    public BangumiUserinfoVO getMe(String accessToken) {
        return blockBangumi(bangumiNextClient.get()
                .uri(ApiConstant.ME)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(BangumiUserinfoVO.class));
    }

    /**
     * 获取每日放送
     */
    @Override
    public CalendarDto getCalendar() {
        log.info("获取每日放送");
        return blockBangumi(bangumiNextClient.get()
                .uri(BangumiApiPath.P1_CALENDAR)
                .retrieve()
                .bodyToMono(CalendarDto.class));
    }

    @Override
    public TrendingSubjectsDto getTrendingSubjects(int type, int limit, int offset) {
        log.info("获取趋势条目 type={} limit={} offset={}", type, limit, offset);
        return blockBangumi(bangumiNextClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(BangumiApiPath.P1_TRENDING_SUBJECTS)
                        .queryParam("type", type)
                        .queryParam("limit", limit)
                        .queryParam("offset", offset)
                        .build())
                .retrieve()
                .bodyToMono(TrendingSubjectsDto.class));
    }

    private <T> T blockBangumi(Mono<T> mono) {
        try {
            return mono.timeout(requestTimeout).block();
        } catch (WebClientResponseException.Unauthorized e) {
            throw new LoginExpiredException(e);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new LoginExpiredException(e);
            }
            throw e;
        } catch (WebClientRequestException e) {
            throw toBangumiUpstreamException(e);
        } catch (RuntimeException e) {
            if (isTimeoutCause(e)) {
                throw new BangumiUpstreamException("Bangumi 服务响应超时，请稍后重试", e);
            }
            throw e;
        }
    }

    private static BangumiUpstreamException toBangumiUpstreamException(WebClientRequestException e) {
        if (isTimeoutCause(e)) {
            return new BangumiUpstreamException("Bangumi 服务响应超时，请稍后重试", e);
        }
        return new BangumiUpstreamException("暂时无法连接 Bangumi，请检查网络后重试", e);
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
