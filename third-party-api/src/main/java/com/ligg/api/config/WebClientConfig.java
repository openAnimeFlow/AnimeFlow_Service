/**
 * @author Ligg
 * @date 2026/6/16 14:50
 */
package com.ligg.api.config;

import com.ligg.common.apipath.BangumiApiPath;
import com.ligg.common.apipath.BangumiNextApiPath;
import com.ligg.common.apipath.BgmTvApiPath;
import com.ligg.common.constants.DandanPlayApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(BangumiProperties.class)
public class WebClientConfig {

    public static final String BANGUMI_NEXT_WEB_CLIENT = "bangumiNextWebClient";

    public static final String BANGUMI_V0_WEB_CLIENT = "bangumiV0WebClient";

    public static final String DANDANPLAY_WEB_CLIENT = "dandanplayWebClient";

    public static final String BGM_TV_WEB_CLIENT = "bgmTvWebClient";

    private static final int MAX_IN_MEMORY_BODY_BYTES = 10 * 1024 * 1024;

    private static final ExchangeStrategies BANGUMI_EXCHANGE_STRATEGIES = ExchangeStrategies.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BODY_BYTES))
            .build();

    @Bean(name = BANGUMI_NEXT_WEB_CLIENT)
    public WebClient bangumiNextWebClient(
            @Value("${anime-flow.bangumi.user-agent}") String bangumiUserAgent,
            @Value("${anime-flow.bangumi.request-timeout-seconds:30}") int requestTimeoutSeconds) {
        Duration requestTimeout = Duration.ofSeconds(Math.max(5, requestTimeoutSeconds));
        HttpClient reactorHttpClient = HttpClient.create().responseTimeout(requestTimeout);
        return WebClient.builder()
                .exchangeStrategies(BANGUMI_EXCHANGE_STRATEGIES)
                .clientConnector(new ReactorClientHttpConnector(reactorHttpClient))
                .baseUrl(BangumiNextApiPath.BANGUMI_NEXT_API_BASE_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, bangumiUserAgent)
                .build();
    }

    @Bean(name = BANGUMI_V0_WEB_CLIENT)
    public WebClient bangumiV0WebClient(
            @Value("${anime-flow.bangumi.user-agent}") String bangumiUserAgent,
            @Value("${anime-flow.bangumi.request-timeout-seconds:30}") int requestTimeoutSeconds) {
        Duration requestTimeout = Duration.ofSeconds(Math.max(5, requestTimeoutSeconds));
        HttpClient reactorHttpClient = HttpClient.create().responseTimeout(requestTimeout);
        return WebClient.builder()
                .exchangeStrategies(BANGUMI_EXCHANGE_STRATEGIES)
                .clientConnector(new ReactorClientHttpConnector(reactorHttpClient))
                .baseUrl(BangumiApiPath.BANGUMI_API_BASE_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, bangumiUserAgent)
                .build();
    }

    @Bean(name = BGM_TV_WEB_CLIENT)
    public WebClient bgmTvWebClient(
            @Value("${anime-flow.bangumi.request-timeout-seconds:30}") int requestTimeoutSeconds) {
        Duration requestTimeout = Duration.ofSeconds(Math.max(5, requestTimeoutSeconds));
        HttpClient reactorHttpClient = HttpClient.create().responseTimeout(requestTimeout);
        return WebClient.builder()
                .exchangeStrategies(BANGUMI_EXCHANGE_STRATEGIES)
                .clientConnector(new ReactorClientHttpConnector(reactorHttpClient))
                .baseUrl(BgmTvApiPath.BGM_TV_BASE_URL)
                .build();
    }

    @Bean(name = DANDANPLAY_WEB_CLIENT)
    public WebClient dandanplayWebClient(
            @Value("${anime-flow.dandanplay.app_id}") String dandanPlayAppId,
            @Value("${anime-flow.dandanplay.secret}") String dandanPlaySecret) {
        return WebClient.builder()
                .baseUrl(DandanPlayApi.DANDAN_PLAY_API_BASE_URL)
                .exchangeStrategies(BANGUMI_EXCHANGE_STRATEGIES)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(true)))
                .defaultHeader("X-AppId", dandanPlayAppId)
                .defaultHeader("X-AppSecret", dandanPlaySecret)
                .build();
    }
}
