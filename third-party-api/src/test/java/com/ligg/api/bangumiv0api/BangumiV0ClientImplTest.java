package com.ligg.api.bangumiv0api;

import com.ligg.common.apipath.BangumiApiPath;
import com.ligg.common.exception.BangumiUpstreamException;
import com.ligg.common.thirdparty.bangumi.enums.SubjectImageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BangumiV0ClientImplTest {

    private static final int SUBJECT_ID = 501958;
    private static final String IMAGE_URL = "https://lain.bgm.tv/pic/cover/l/c/abc.jpg";

    private DisposableServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.disposeNow();
        }
    }

    @Test
    void getSubjectImageUrl_returnsLocationOn302() {
        AtomicReference<String> lastPath = new AtomicReference<>();
        server = startServer((request, response) -> {
            lastPath.set(request.uri());
            return response.status(302).addHeader("Location", IMAGE_URL).send();
        });

        BangumiV0ClientImpl client = newClient(server.port());
        String url = client.getSubjectImageUrl(SUBJECT_ID, SubjectImageType.LARGE);

        // 单元测试走本地 mock 302，地址即下方预设的 Location（非 Bangumi 真实 CDN）
        System.out.println("[unit-test] subjectId=" + SUBJECT_ID + " imageUrl=" + url);

        assertEquals(IMAGE_URL, url);
        assertTrue(lastPath.get().startsWith("/v0/subjects/" + SUBJECT_ID + "/image"));
        assertTrue(lastPath.get().contains("type=large"));
    }

    @Test
    void getSubjectImageUrl_passesImageTypeQueryParam() {
        AtomicReference<String> lastPath = new AtomicReference<>();
        server = startServer((request, response) -> {
            lastPath.set(request.uri());
            return response.status(302).addHeader("Location", IMAGE_URL).send();
        });

        BangumiV0ClientImpl client = newClient(server.port());
        client.getSubjectImageUrl(SUBJECT_ID, SubjectImageType.GRID);

        assertTrue(lastPath.get().contains("type=grid"));
    }

    @Test
    void getSubjectImageUrl_throwsWhen302WithoutLocation() {
        server = startServer((request, response) -> response.status(302).send());

        BangumiV0ClientImpl client = newClient(server.port());
        BangumiUpstreamException ex = assertThrows(
                BangumiUpstreamException.class,
                () -> client.getSubjectImageUrl(SUBJECT_ID, SubjectImageType.LARGE));

        assertTrue(ex.getMessage().contains("未返回 Location"));
    }

    @Test
    void getSubjectImageUrl_throwsOn404() {
        server = startServer((request, response) -> response.status(404).send());

        BangumiV0ClientImpl client = newClient(server.port());
        assertThrows(BangumiUpstreamException.class,
                () -> client.getSubjectImageUrl(SUBJECT_ID, SubjectImageType.LARGE));
    }

    @Test
    void getSubjectImageUrl_throwsOnUnexpected200() {
        server = startServer((request, response) -> response.status(200).send());

        BangumiV0ClientImpl client = newClient(server.port());
        BangumiUpstreamException ex = assertThrows(
                BangumiUpstreamException.class,
                () -> client.getSubjectImageUrl(SUBJECT_ID, SubjectImageType.LARGE));

        assertTrue(ex.getMessage().contains("响应异常"));
    }

    /**
     * 手动启用：请求 Bangumi 上游，在控制台输出真实封面 URL。
     */
    @Test
    @Disabled("默认跳过；去掉 @Disabled 后单独运行此用例以验证上游")
    void getSubjectImageUrl_printsRealUrlFromBangumi() {
        WebClient webClient = WebClient.builder()
                .baseUrl(BangumiApiPath.BANGUMI_API_BASE_URL)
                .build();
        BangumiV0ClientImpl client = new BangumiV0ClientImpl(webClient, 30);

        String url = client.getSubjectImageUrl(SUBJECT_ID, SubjectImageType.LARGE);
        System.out.println("[integration] subjectId=" + SUBJECT_ID + " imageUrl=" + url);

        assertTrue(StringUtils.hasText(url));
        assertTrue(url.startsWith("http"));
    }

    private static BangumiV0ClientImpl newClient(int port) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
        return new BangumiV0ClientImpl(webClient, 30);
    }

    private static DisposableServer startServer(
            BiFunction<HttpServerRequest, HttpServerResponse, Mono<Void>> handler) {
        return HttpServer.create()
                .port(0)
                .route(routes -> routes.get("/v0/subjects/{id}/image", handler::apply))
                .bindNow();
    }
}
