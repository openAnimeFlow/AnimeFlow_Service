package com.ligg.flowclient.service;

import com.ligg.common.exception.AccessTokenExpiredException;
import com.ligg.common.handler.GlobalExceptionHandler;
import com.ligg.common.statuenum.BgmCollectionSyncStatus;
import com.ligg.flowclient.controller.CollectionController;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.module.vo.UserBgmCollectionSyncStatusVo;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CollectionSyncSseTest {
    final CollectionSyncTaskService tasks = mock(CollectionSyncTaskService.class);
    final JwtTokenService jwt = mock(JwtTokenService.class);
    final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    final CollectionSyncSseService streams = new CollectionSyncSseService(tasks, jwt, redis);
    MockMvc mvc;

    @BeforeEach void setup() {
        when(jwt.validateAccessToken("first")).thenReturn(10L);
        when(jwt.validateAccessToken("second")).thenReturn(20L);
        when(tasks.status(10L)).thenReturn(snapshot(10L, 1L));
        when(tasks.status(20L)).thenReturn(snapshot(20L, 1L));
        mvc = standaloneSetup(new CollectionController(streams, jwt,
                mock(BangumiOAuthTokenService.class), mock(UserBgmCollectionService.class),
                mock(UserBgmCollectionSyncService.class)))
                .addInterceptors(new AuthorizationInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach void cleanup() { streams.close(); }

    @Test void initialSnapshotCommittedChangesHeartbeatAndUserIsolation() throws Exception {
        var first = connect("first");
        var second = connect("second");
        contains(first, "id:10:1");
        contains(second, "id:20:1");
        when(tasks.status(10L)).thenReturn(snapshot(10L, 3L));
        streams.changed(new CollectionSyncEvents.Changed(10L));
        streams.changed(new CollectionSyncEvents.Changed(10L));
        streams.flushChanges();
        contains(first, "id:10:3");
        verify(redis, times(1)).convertAndSend(CollectionSyncSseService.CHANNEL, "10");
        assertFalse(second.getResponse().getContentAsString().contains("id:10:"));
        streams.heartbeat();
        contains(first, ":heartbeat");
        verify(tasks, times(2)).status(10L); // Heartbeat performs no task query.
        // A late broker notification with an older snapshot cannot regress a connection.
        when(tasks.status(10L)).thenReturn(snapshot(10L, 2L));
        streams.onMessage(new DefaultMessage(new byte[0], "10".getBytes(StandardCharsets.UTF_8)), null);
        streams.flushChanges();
        streams.heartbeat();
        contains(first, ":heartbeat");
        assertFalse(first.getResponse().getContentAsString().contains("id:10:2"));
    }

    @Test void brokerNotificationReachesLocalConnectionWithoutRepublishing() throws Exception {
        var first = connect("first");
        contains(first, "id:10:1");
        when(tasks.status(10L)).thenReturn(snapshot(10L, 2L));
        streams.onMessage(new DefaultMessage(new byte[0], "10".getBytes(StandardCharsets.UTF_8)), null);
        streams.flushChanges();
        contains(first, "id:10:2");
        verifyNoInteractions(redis);
    }

    @Test void completedConnectionIsRemovedAndReconnectReadsCurrentSnapshot() throws Exception {
        var first = connect("first");
        contains(first, "id:10:1");
        first.getRequest().getAsyncContext().complete();
        clearInvocations(tasks);
        streams.changed(new CollectionSyncEvents.Changed(10L));
        streams.flushChanges();
        verifyNoInteractions(tasks);
        when(tasks.status(10L)).thenReturn(snapshot(10L, 4L));
        contains(connect("first"), "id:10:4");
    }

    @Test void heartbeatClosesRevokedSessionAndDoesNotSendFurtherData() throws Exception {
        var first = connect("first");
        contains(first, "id:10:1");
        when(jwt.validateAccessToken("first")).thenThrow(new AccessTokenExpiredException());
        streams.heartbeat();
        await().atMost(Duration.ofSeconds(3)).until(() -> first.getAsyncResult(3000) == null);
        clearInvocations(tasks);
        streams.changed(new CollectionSyncEvents.Changed(10L));
        streams.flushChanges();
        verifyNoInteractions(tasks);
    }

    @Test void unauthenticatedHandshakeReturnsJsonInsteadOfOpeningStream() throws Exception {
        when(jwt.validateAccessToken("expired")).thenThrow(new AccessTokenExpiredException());
        mvc.perform(get("/api/v1/users/collections/sync/events")
                        .header("Authorization", "Bearer expired")
                        .header("Accept", "text/event-stream, application/json"))
                .andExpect(request().asyncNotStarted())
                .andExpect(jsonPath("$.authReason").value("access_token_expired"));
        verifyNoInteractions(tasks);
    }

    private MvcResult connect(String token) throws Exception {
        var result = mvc.perform(get("/api/v1/users/collections/sync/events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(request().asyncStarted())
                .andReturn();
        contains(result, "event:status");
        assertEquals("no", result.getResponse().getHeader("X-Accel-Buffering"));
        assertTrue(result.getResponse().getContentType().startsWith("text/event-stream"));
        return result;
    }

    private void contains(MvcResult result, String text) {
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertTrue(result.getResponse().getContentAsString().contains(text)));
    }

    private static UserBgmCollectionSyncStatusVo snapshot(long user, long version) {
        var status = new UserBgmCollectionSyncStatusVo();
        status.setUserId(user); status.setTaskId(user); status.setStatusVersion(version);
        status.setStatus(BgmCollectionSyncStatus.WAITING_CONFLICT);
        status.setPendingConflictCount(1);
        return status;
    }
}
