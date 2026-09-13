package com.ligg.flowclient.service;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.ligg.common.exception.AccessTokenExpiredException;
import com.ligg.common.exception.RefreshTokenInvalidException;
import com.ligg.common.response.FlowTokenVo;
import com.ligg.common.statuenum.Platform;
import com.ligg.flowclient.config.JwtProperties;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Uses an isolated real Redis process; never connects to application Redis. */
class JwtTokenServiceRedisTest {
    static Process redisProcess;
    static LettuceConnectionFactory factory;
    static RedisTemplate<String, Object> redis;
    JwtTokenService service;
    JwtTokenService secondInstance;

    @BeforeAll
    static void startRedis() throws Exception {
        String executable = System.getenv("REDIS_TEST_EXECUTABLE");
        Assumptions.assumeTrue(executable != null, "Set REDIS_TEST_EXECUTABLE to run real Redis tests");
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        redisProcess = new ProcessBuilder(executable, "--bind", "127.0.0.1", "--port", String.valueOf(port),
                "--save", "", "--appendonly", "no", "--loglevel", "warning")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        factory = new LettuceConnectionFactory("127.0.0.1", port);
        factory.afterPropertiesSet();
        factory.start();
        redis = new RedisTemplate<>();
        redis.setConnectionFactory(factory);
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY);
        redis.setKeySerializer(new StringRedisSerializer());
        redis.setValueSerializer(new Jackson2JsonRedisSerializer<>(mapper, Object.class));
        redis.afterPropertiesSet();
        for (int i = 0; i < 50; i++) {
            try (var connection = factory.getConnection()) {
                if ("PONG".equals(connection.ping())) return;
            } catch (RuntimeException ignored) { Thread.sleep(100); }
        }
        fail("Isolated test Redis did not start");
    }

    @AfterAll
    static void stopRedis() throws Exception {
        if (factory != null) factory.destroy();
        if (redisProcess != null) {
            redisProcess.destroy();
            if (!redisProcess.waitFor(5, TimeUnit.SECONDS)) redisProcess.destroyForcibly();
        }
    }

    @BeforeEach
    void setup() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-only-secret-32-characters-long-123456");
        properties.setRefreshRetrySeconds(2);
        service = new JwtTokenService(properties, redis);
        secondInstance = new JwtTokenService(properties, redis);
        service.init();
        secondInstance.init();
    }

    FlowTokenVo login() { return service.issueToken(42L, "test@example.test", Platform.WINDOWS); }

    @Test
    void concurrentRefreshAcrossInstancesReturnsOnePairAndRenewsSevenDays() throws Exception {
        FlowTokenVo original = login();
        ExecutorService workers = Executors.newFixedThreadPool(12);
        try {
            CountDownLatch start = new CountDownLatch(1);
            var futures = new ArrayList<Future<FlowTokenVo>>();
            for (int i = 0; i < 12; i++) {
                JwtTokenService instance = i % 2 == 0 ? service : secondInstance;
                futures.add(workers.submit(() -> { start.await(); return instance.refreshToken(original.getRefreshToken()); }));
            }
            start.countDown();
            FlowTokenVo winner = futures.get(0).get(10, TimeUnit.SECONDS);
            for (var future : futures) {
                FlowTokenVo actual = future.get(10, TimeUnit.SECONDS);
                assertEquals(winner.getAccessToken(), actual.getAccessToken());
                assertEquals(winner.getRefreshToken(), actual.getRefreshToken());
                assertEquals(original.getSessionId(), actual.getSessionId());
            }
            assertEquals(42L, service.validateAccessToken(winner.getAccessToken()));
            long ttl = redis.getExpire("animeflow:auth:session:" + original.getSessionId());
            assertTrue(ttl > 604790 && ttl <= 604800);
        } finally { workers.shutdownNow(); }
    }

    @Test
    void lostResponseCanBeRetriedWithoutRotatingAgain() {
        FlowTokenVo original = login();
        FlowTokenVo rotated = service.refreshToken(original.getRefreshToken());
        FlowTokenVo retry = secondInstance.refreshToken(original.getRefreshToken());
        assertEquals(rotated.getRefreshToken(), retry.getRefreshToken());
        assertEquals(rotated.getAccessToken(), retry.getAccessToken());
        assertTrue(retry.getRefreshExpiresIn() <= rotated.getRefreshExpiresIn());
    }

    @Test
    void oldRefreshAndAccessStopWorkingAfterGrace() throws Exception {
        FlowTokenVo original = login();
        FlowTokenVo rotated = service.refreshToken(original.getRefreshToken());
        assertEquals(42L, service.validateAccessToken(original.getAccessToken()));
        Thread.sleep(2200);
        assertThrows(RefreshTokenInvalidException.class, () -> service.refreshToken(original.getRefreshToken()));
        assertThrows(AccessTokenExpiredException.class, () -> service.validateAccessToken(original.getAccessToken()));
        assertEquals(42L, service.validateAccessToken(rotated.getAccessToken()));
    }

    @Test
    void replayNeverReturnsAReplacedGeneration() {
        FlowTokenVo original = login();
        FlowTokenVo first = service.refreshToken(original.getRefreshToken());
        FlowTokenVo second = service.refreshToken(first.getRefreshToken());
        assertThrows(RefreshTokenInvalidException.class, () -> service.refreshToken(original.getRefreshToken()));
        assertEquals(second.getRefreshToken(), service.refreshToken(first.getRefreshToken()).getRefreshToken());
    }

    @Test
    void logoutInvalidatesCurrentAndGraceTokensAndReplay() {
        FlowTokenVo original = login();
        FlowTokenVo rotated = service.refreshToken(original.getRefreshToken());
        service.logout(original.getAccessToken());
        service.logout(original.getAccessToken());
        assertThrows(AccessTokenExpiredException.class, () -> service.validateAccessToken(original.getAccessToken()));
        assertThrows(AccessTokenExpiredException.class, () -> service.validateAccessToken(rotated.getAccessToken()));
        assertThrows(RefreshTokenInvalidException.class, () -> service.refreshToken(original.getRefreshToken()));
        assertThrows(RefreshTokenInvalidException.class, () -> service.refreshToken(rotated.getRefreshToken()));
    }

    @Test
    void revokeAllUserSessionsInvalidatesEveryTokenPair() {
        FlowTokenVo first = service.issueToken(42L, "test@example.test", Platform.WINDOWS);
        FlowTokenVo second = service.issueToken(42L, "test@example.test", Platform.ANDROID);

        service.revokeAllUserSessions(42L);

        assertThrows(AccessTokenExpiredException.class, () -> service.validateAccessToken(first.getAccessToken()));
        assertThrows(RefreshTokenInvalidException.class, () -> service.refreshToken(first.getRefreshToken()));
        assertThrows(AccessTokenExpiredException.class, () -> service.validateAccessToken(second.getAccessToken()));
        assertThrows(RefreshTokenInvalidException.class, () -> service.refreshToken(second.getRefreshToken()));
    }

    @Test
    void emailUpdatePreservesSessionTtlAndRefreshReplay() {
        FlowTokenVo original = login();
        FlowTokenVo rotated = service.refreshToken(original.getRefreshToken());
        String key = "animeflow:auth:session:" + original.getSessionId();
        redis.expire(key, 60, TimeUnit.SECONDS);
        service.updateUserEmail(42L, "new@example.test");
        assertTrue(redis.getExpire(key) <= 60);
        assertEquals(rotated.getRefreshToken(), service.refreshToken(original.getRefreshToken()).getRefreshToken());
        assertEquals(42L, service.validateAccessToken(rotated.getAccessToken()));
    }

    @Test
    void concurrentLogoutNeverRecreatesSession() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 20; i++) {
                FlowTokenVo original = login();
                CountDownLatch start = new CountDownLatch(1);
                Future<?> refresh = workers.submit(() -> {
                    start.await();
                    try { secondInstance.refreshToken(original.getRefreshToken()); }
                    catch (RefreshTokenInvalidException expected) { /* logout won */ }
                    return null;
                });
                Future<?> logout = workers.submit(() -> { start.await(); service.logout(original.getAccessToken()); return null; });
                start.countDown();
                refresh.get(10, TimeUnit.SECONDS);
                logout.get(10, TimeUnit.SECONDS);
                assertFalse(redis.hasKey("animeflow:auth:session:" + original.getSessionId()));
                assertThrows(AccessTokenExpiredException.class, () -> service.validateAccessToken(original.getAccessToken()));
                assertThrows(RefreshTokenInvalidException.class, () -> service.refreshToken(original.getRefreshToken()));
            }
        } finally { workers.shutdownNow(); }
    }

    @Test
    void malformedOrWrongTypeRefreshIsRejected() {
        assertThrows(RefreshTokenInvalidException.class, () -> service.refreshToken("not-a-jwt"));
        assertThrows(RefreshTokenInvalidException.class, () -> service.refreshToken(login().getAccessToken()));
    }
}
