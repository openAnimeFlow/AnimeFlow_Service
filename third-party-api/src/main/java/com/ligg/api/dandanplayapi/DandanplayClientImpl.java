/**
 * @author Ligg
 * @date 2026/5/9 18:27
 */
package com.ligg.api.dandanplayapi;

import com.ligg.api.config.WebClientConfig;
import com.ligg.common.apipath.DandanPlayApiPath;
import com.ligg.common.vo.dandanplay.DandanplayBangumiDetailVo;
import com.ligg.common.vo.dandanplay.DandanplayCommentVo;
import com.ligg.common.vo.dandanplay.DandanplayEpisodeVo;
import com.ligg.common.vo.dandanplay.DandanplaySearchVo;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Slf4j
@Validated
@Service
public class DandanplayClientImpl implements DandanplayClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final WebClient dandanPlayClient;

    public DandanplayClientImpl(
            @Qualifier(WebClientConfig.DANDANPLAY_WEB_CLIENT) WebClient webClient) {
        dandanPlayClient = webClient;
    }

    @Override
    public DandanplayCommentVo getDanmaku(int episodeId, Boolean withRelated, int chConvert) {
        ResponseEntity<DandanplayCommentVo> response = dandanPlayClient.get()
                .uri(uriBuilder -> {
                    var b = uriBuilder.path(DandanPlayApiPath.DANDAN_API_COMMENT + episodeId)
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
        return response.getBody();
    }

    @Override
    public DandanplaySearchVo searchAnimes(String keyword, Integer type) {
        ResponseEntity<DandanplaySearchVo> response = dandanPlayClient.get()
                .uri(uriBuilder -> {
                    var b = uriBuilder.path(DandanPlayApiPath.DANDAN_API_SEARCH_ANIME)
                            .queryParam("keyword", keyword);
                    if (type != null) {
                        b = b.queryParam("type", type);
                    }
                    return b.build();
                })
                .retrieve()
                .toEntity(DandanplaySearchVo.class)
                .block(REQUEST_TIMEOUT);
        return response.getBody();
    }

    @Override
    public DandanplaySearchVo searchEpisodes(String anime, Boolean v2) {
        ResponseEntity<DandanplaySearchVo> response = dandanPlayClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(DandanPlayApiPath.DANDAN_API_SEARCH_EPISODES)
                        .queryParam("anime", anime)
                        .queryParam("v2", v2 == null || v2)
                        .build())
                .retrieve()
                .toEntity(DandanplaySearchVo.class)
                .block(REQUEST_TIMEOUT);
        return response.getBody();
    }

    @Override
    public DandanplayBangumiDetailVo getBangumiDetail(@NotNull int bangumiId) {
        ResponseEntity<DandanplayBangumiDetailVo> response = dandanPlayClient.get()
                .uri(uriBuilder -> uriBuilder.path(DandanPlayApiPath.DANDAN_API_ELEMENT + '/' + bangumiId).build())
                .retrieve()
                .toEntity(DandanplayBangumiDetailVo.class)
                .block(REQUEST_TIMEOUT);
        return response.getBody();
    }

    @Override
    public DandanplayEpisodeVo getBangumiDetailByBangumiId(@NotNull int bangumiId) {
        ResponseEntity<DandanplayEpisodeVo> response = dandanPlayClient.get()
                .uri(uriBuilder -> uriBuilder.path(DandanPlayApiPath.DANDAN_API_ELEMENT_BY_BANGUMI_ID + '/' + bangumiId).build())
                .retrieve()
                .toEntity(DandanplayEpisodeVo.class)
                .block(REQUEST_TIMEOUT);
        return response.getBody();
    }
}
