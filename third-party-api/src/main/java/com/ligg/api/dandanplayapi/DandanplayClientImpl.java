/**
 * @author Ligg
 * @date 2026/5/9 18:27
 */
package com.ligg.api.dandanplayapi;

import com.ligg.common.constants.ApiConstant;
import com.ligg.common.exception.LoginExpiredException;
import com.ligg.common.vo.DandanplayCommentVo;
import com.ligg.common.vo.DanmakuVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class DandanplayClientImpl implements DandanplayClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String DANDANPLAY_API_BASE_URL = ApiConstant.DANDAN_PLAY_API_BASE_URL;

    @Value("${dandanplay.app_id}")
    private String dandanPlayAppId;

    @Value("${dandanplay.secret}")
    private String dandanPlaySecret;


    private final WebClient webClient = WebClient.builder()
            .baseUrl(DANDANPLAY_API_BASE_URL)
            .build();

    @Override
    public List<DanmakuVo> getDanmaku(int episodeId, Boolean withRelated, int chConvert) {
        try {
            DandanplayCommentVo body = webClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path(ApiConstant.DANDAN_API_COMMENT + episodeId)
                                .queryParam("chConvert", chConvert)
                                .queryParam("from", 0);
                        if (withRelated != null) {
                            b = b.queryParam("withRelated", withRelated);
                        }
                        return b.build();
                    })
                    .header("X-AppId", dandanPlayAppId)
                    .header("X-AppSecret", dandanPlaySecret)
                    .retrieve()
                    .bodyToMono(DandanplayCommentVo.class)
                    .block(REQUEST_TIMEOUT);
            if (body == null || body.comments() == null) {
                return List.of();
            }
            return body.comments();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new LoginExpiredException(e);
            }
            throw e;
        }
    }
}
