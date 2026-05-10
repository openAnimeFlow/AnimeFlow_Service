/**
 * @author Ligg
 * @date 2026/5/9 18:27
 */
package com.ligg.api.dandanplayapi;

import com.ligg.common.constants.DandanPlayApi;
import com.ligg.common.exception.LoginExpiredException;
import com.ligg.common.vo.dandanplay.BangumiDetailVo;
import com.ligg.common.vo.dandanplay.DandanplayCommentVo;
import com.ligg.common.vo.dandanplay.DanmakuSearchVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Slf4j
@Validated
@Service
public class DandanplayClientImpl implements DandanplayClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    /**
     * 弹幕 JSON 可能很大，需高于 WebClient 默认 256KB
     **/
    private static final int MAX_IN_MEMORY_BODY_BYTES = 1024 * 1024;
    private static final String DANDANPLAY_API_BASE_URL = DandanPlayApi.DANDAN_PLAY_API_BASE_URL;

    private static final ExchangeStrategies DANDAN_EXCHANGE_STRATEGIES = ExchangeStrategies.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BODY_BYTES))
            .build();

    private final WebClient webClient;

    public DandanplayClientImpl(
            @Value("${anime-flow.dandanplay.app_id}") String dandanPlayAppId,
            @Value("${anime-flow.dandanplay.secret}") String dandanPlaySecret) {
        this.webClient = WebClient.builder()
                .baseUrl(DANDANPLAY_API_BASE_URL)
                .exchangeStrategies(DANDAN_EXCHANGE_STRATEGIES)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(true)))
                .defaultHeader("X-AppId", dandanPlayAppId)
                .defaultHeader("X-AppSecret", dandanPlaySecret)
                .build();
    }

    @Override
    public DandanplayCommentVo getDanmaku(int episodeId, Boolean withRelated, int chConvert) {
        try {
            ResponseEntity<DandanplayCommentVo> response = webClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path(DandanPlayApi.DANDAN_API_COMMENT + episodeId)
                                .queryParam("chConvert", chConvert)
                                .queryParam("from", 0);
                        if (withRelated != null) {
                            b = b.queryParam("withRelated", withRelated);
                        }
                        return b.build();
                    })
                    .retrieve()
                    .toEntity(DandanplayCommentVo.class)
                    .block(REQUEST_TIMEOUT);
            log.info("弹弹play 获取弹幕请求响应状态码: {}", response.getStatusCode().value());
            return response.getBody();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new LoginExpiredException(e);
            }
            throw e;
        }
    }

    @Override
    public DanmakuSearchVo searchAnimes(String keyword, Integer type) {
        try {
            ResponseEntity<DanmakuSearchVo> response = webClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path(DandanPlayApi.DANDAN_API_SEARCH_ANIME)
                                .queryParam("keyword", keyword);
                        if (type != null) {
                            b = b.queryParam("type", type);
                        }
                        return b.build();
                    })
                    .retrieve()
                    .toEntity(DanmakuSearchVo.class)
                    .block(REQUEST_TIMEOUT);
            log.info("弹弹play 搜索番剧响应状态码: {}", response.getStatusCode().value());
            return response.getBody();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new LoginExpiredException(e);
            }
            throw e;
        }
    }

    @Override
    public BangumiDetailVo getBangumiDetail(int bangumiId) {
        try {
            ResponseEntity<BangumiDetailVo> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path(DandanPlayApi.DANDAN_API_BANGUMI + '/' + bangumiId).build())
                    .retrieve()
                    .toEntity(BangumiDetailVo.class)
                    .block(REQUEST_TIMEOUT);
            log.info("弹弹play 获取番剧详情响应状态码: {}", response.getStatusCode().value());
            return response.getBody();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new LoginExpiredException(e);
            }
            throw e;
        }
    }
}
