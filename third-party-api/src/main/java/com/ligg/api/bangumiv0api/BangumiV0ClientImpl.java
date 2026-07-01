package com.ligg.api.bangumiv0api;

import com.ligg.api.config.WebClientConfig;
import com.ligg.common.apipath.BangumiApiPath;
import com.ligg.common.exception.BangumiUpstreamException;
import com.ligg.common.thirdparty.bangumi.enums.SubjectImageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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
public class BangumiV0ClientImpl implements BangumiV0Client {

    private final Duration requestTimeout;
    private final WebClient bangumiV0Client;

    public BangumiV0ClientImpl(
            @Qualifier(WebClientConfig.BANGUMI_V0_WEB_CLIENT) WebClient bangumiV0Client,
            @Value("${anime-flow.bangumi.request-timeout-seconds:30}") int requestTimeoutSeconds) {
        this.requestTimeout = Duration.ofSeconds(Math.max(5, requestTimeoutSeconds));
        HttpClient noRedirectHttpClient = HttpClient.create()
                .followRedirect(false)
                .responseTimeout(this.requestTimeout);
        this.bangumiV0Client = bangumiV0Client.mutate()
                .clientConnector(new ReactorClientHttpConnector(noRedirectHttpClient))
                .build();
    }

    @Override
    public String getSubjectImageUrl(int subjectId, SubjectImageType type, String accessToken) {
        return block(bangumiV0Client.get()
                .uri(uriBuilder -> uriBuilder
                        .path(BangumiApiPath.V0_SUBJECT_IMAGE)
                        .queryParam("type", type.getValue())
                        .build(subjectId))
                .headers(httpHeaders -> {
                    if (StringUtils.hasText(accessToken)) {
                        httpHeaders.setBearerAuth(accessToken);
                    }
                })
                .exchangeToMono(response -> {
                    HttpStatusCode status = response.statusCode();
                    if (status.is3xxRedirection()) {
                        String location = response.headers().asHttpHeaders().getFirst(HttpHeaders.LOCATION);
                        if (!StringUtils.hasText(location)) {
                            return Mono.error(new BangumiUpstreamException(
                                    "Bangumi 条目图片未返回 Location, subjectId=" + subjectId));
                        }
                        return response.releaseBody().then(Mono.just(location));
                    }
                    if (status.is4xxClientError() || status.is5xxServerError()) {
                        return response.createException().flatMap(Mono::error);
                    }
                    return Mono.error(new BangumiUpstreamException(
                            "Bangumi 条目图片响应异常 status=" + status.value() + ", subjectId=" + subjectId));
                }));
    }

    private <T> T block(Mono<T> mono) {
        try {
            return mono.timeout(requestTimeout).block();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                String responseBody = e.getResponseBodyAsString();
                log.warn("Bangumi v0 请求被拒绝 status={} body={}", e.getStatusCode().value(), responseBody);
                throw new BangumiUpstreamException("Bangumi 请求参数无效: " + responseBody, e);
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
