package com.ligg.flow_bootstrap;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ligg.common.statuenum.Platform;
import com.ligg.flowclient.service.JwtTokenService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Starts the application with H2 and a private Redis instance, without running startup import jobs. */
@EnabledIfEnvironmentVariable(named = "REDIS_TEST_EXECUTABLE", matches = ".+")
class Boot41Jackson3SmokeTest {

    private static Process redis;
    private static int redisPort;

    @BeforeAll
    static void startRedis() throws Exception {
        try (var socket = new ServerSocket(0)) {
            redisPort = socket.getLocalPort();
        }
        redis = new ProcessBuilder(System.getenv("REDIS_TEST_EXECUTABLE"), "--bind", "127.0.0.1",
                "--port", String.valueOf(redisPort), "--save", "", "--appendonly", "no", "--loglevel", "warning")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        for (int attempt = 0; attempt < 50; attempt++) {
            try (var socket = new Socket("127.0.0.1", redisPort)) {
                return;
            } catch (Exception ignored) {
                Thread.sleep(100);
            }
        }
        fail("Isolated test Redis did not start");
    }

    @AfterAll
    static void stopRedis() throws Exception {
        if (redis != null) {
            redis.destroy();
            if (!redis.waitFor(5, TimeUnit.SECONDS)) redis.destroyForcibly();
        }
    }

    @Test
    void applicationStartsWithNativeJackson3DatabaseRedisJwtAndVersionedMappings() {
        new WebApplicationContextRunner()
                .withUserConfiguration(AnimeFlowApplication.class, ProbeController.class)
                .withPropertyValues(
                        "server.port=0",
                        "spring.datasource.url=jdbc:h2:mem:boot41_upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.username=sa", "spring.datasource.password=",
                        "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=" + redisPort,
                        "spring.data.redis.timeout=2s",
                        "anime-flow.jwt.secret=upgrade-test-secret-at-least-32-characters",
                        "anime-flow.api-auth.enabled=false",
                        "anime-flow.bangumi.user-agent=AnimeFlow-upgrade-test",
                        "anime-flow.dandanplay.app_id=test", "anime-flow.dandanplay.secret=test",
                        "anime-flow.resend.api_key=test", "anime-flow.resend.domain=example.test",
                        "anime-flow.cloudflare.r2.endpoint=https://storage.example.test",
                        "anime-flow.cloudflare.r2.access-key=test", "anime-flow.cloudflare.r2.secret-key=test",
                        "anime-flow.cloudflare.r2.bucket=test", "anime-flow.cloudflare.r2.public-url=https://images.example.test",
                        "anime-flow.sync.archive.enabled=false", "anime-flow.sync.background-image.enabled=false",
                        "anime-flow.sync.bangumi-data.enabled=false", "anime-flow.access-log.enabled=false",
                        "anime-flow.access-log.archive.enabled=false", "anime-flow.collection-sync.initial-delay-ms=3600000")
                .run(context -> {
                    if (context.getStartupFailure() != null) {
                        throw new AssertionError("Application context did not start", context.getStartupFailure());
                    }
                    assertNotNull(context.getBean(JsonMapper.class));
                    assertNotNull(context.getBean(SqlSessionFactory.class));
                    assertFalse(ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", getClass().getClassLoader()),
                            "Jackson 2 databind must not be on the runtime classpath");
                    var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).build();
                    mvc.perform(get("/upgrade-check/json"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.created_at").value("2026-10-20T12:30:00"));
                    mvc.perform(post("/upgrade-check/version").header("X-Api-Version", "1.0"))
                            .andExpect(status().isOk()).andExpect(content().string("v1"));
                    mvc.perform(post("/upgrade-check/version").header("X-Api-Version", "2.0"))
                            .andExpect(status().isOk()).andExpect(content().string("v2"));
                    mvc.perform(post("/api/danmaku").header("X-Api-Version", "2")
                                    .contentType("application/json").content("""
                                            {"subjectId":70131,"episodeId":260269,"comment":"hello",
                                             "time":1,"color":16777215,"type":1}
                                            """))
                            .andExpect(jsonPath("$.code").value(401));
                    mvc.perform(post("/api/danmaku").header("X-Api-Version", "2")
                                    .contentType("application/json").content("{}"))
                            .andExpect(jsonPath("$.code").value(401));
                    mvc.perform(get("/api/danmaku").header("X-Api-Version", "2")
                                    .param("subjectId", "0").param("episodeId", "260269"))
                            .andExpect(jsonPath("$.code").value(400));
                    mvc.perform(get("/api/danmaku").header("X-Api-Version", "2")
                                    .param("episodeId", "260269"))
                            .andExpect(jsonPath("$.code").value(400));
                    mvc.perform(post("/api/danmaku").header("Authorization", "Bearer unused")
                                    .contentType("application/json").content("{}"))
                            .andExpect(jsonPath("$.code").value(404));
                    JwtTokenService tokens = context.getBean(JwtTokenService.class);
                    var pair = tokens.issueToken(42L, "upgrade@example.test", Platform.WINDOWS);
                    assertEquals(42L, tokens.validateAccessToken(pair.getAccessToken()));
                    var refreshed = tokens.refreshToken(pair.getRefreshToken());
                    assertEquals(42L, tokens.validateAccessToken(refreshed.getAccessToken()));
                });
    }

    @RestController
    static class ProbeController {
        @GetMapping("/upgrade-check/json")
        TimestampResponse json() {
            return new TimestampResponse(LocalDateTime.of(2026, 10, 20, 12, 30));
        }

        @PostMapping(path = "/upgrade-check/version", version = "1.0")
        String firstVersion() {
            return "v1";
        }

        @PostMapping(path = "/upgrade-check/version", version = "2.0")
        String secondVersion() {
            return "v2";
        }
    }

    record TimestampResponse(@JsonProperty("created_at") LocalDateTime createdAt) {
    }
}
