package com.ligg.flowclient.service;

import com.ligg.flowclient.controller.PresenceController;
import com.ligg.flowclient.module.vo.OnlineCountVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class PresenceSseTest {

    @Mock private PresenceService presenceService;
    private PresenceSseService streams;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        streams = new PresenceSseService(presenceService);
        streams.start();
        mvc = standaloneSetup(new PresenceController(presenceService, streams)).build();
    }

    @AfterEach
    void tearDown() {
        streams.stop();
    }

    @Test
    void streamHasNoForcedFiveMinuteTimeoutAndSendsInitialSnapshot() throws Exception {
        when(presenceService.onlineCount()).thenReturn(count(1));

        MvcResult result = mvc.perform(get("/api/v1/presence/online-count"))
                .andExpect(request().asyncStarted())
                .andReturn();

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertTrue(result.getResponse().getContentAsString().contains("event:online-count"));
            assertTrue(result.getResponse().getContentAsString()
                    .contains("\"onlineDevices\":1"));
        });
        assertEquals(0L, streams.subscribeOnlineCount().getTimeout());
    }

    @Test
    void slowSubscriberDoesNotBlockAnotherSubscriber() throws Exception {
        CountDownLatch slowQueryStarted = new CountDownLatch(1);
        CountDownLatch releaseSlowQuery = new CountDownLatch(1);
        when(presenceService.subjectOnlineCount(42, "slow")).thenAnswer(ignored -> {
            slowQueryStarted.countDown();
            assertTrue(releaseSlowQuery.await(5, TimeUnit.SECONDS));
            return count(1);
        });
        when(presenceService.subjectOnlineCount(42, "fast"))
                .thenReturn(count(1));

        MvcResult slow = connectSubject("slow");
        try {
            assertTrue(slowQueryStarted.await(3, TimeUnit.SECONDS));
            MvcResult fast = connectSubject("fast");
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
                assertTrue(fast.getResponse().getContentAsString()
                        .contains("\"onlineDevices\":1"));
            });
            assertTrue(slow.getResponse().getContentAsString().isEmpty());
        } finally {
            releaseSlowQuery.countDown();
        }
    }

    @Test
    void changeBroadcastUsesOneSubjectSnapshotForSubscribers() throws Exception {
        when(presenceService.subjectOnlineCount(42, "first")).thenReturn(count(1));
        when(presenceService.subjectOnlineCount(42, "second")).thenReturn(count(1));
        when(presenceService.subjectOnlineCounts(42, Set.of("first", "second")))
                .thenReturn(new PresenceService.SubjectOnlineCounts(count(3),
                        Map.of("first", count(2), "second", count(2))));
        MvcResult first = connectSubject("first");
        MvcResult second = connectSubject("second");
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertTrue(first.getResponse().getContentAsString()
                    .contains("\"onlineDevices\":1"));
            assertTrue(second.getResponse().getContentAsString()
                    .contains("\"onlineDevices\":1"));
        });

        streams.onMessage(new DefaultMessage(new byte[0],
                "changed".getBytes(StandardCharsets.UTF_8)), null);
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertTrue(first.getResponse().getContentAsString()
                    .contains("\"onlineDevices\":2"));
            assertTrue(second.getResponse().getContentAsString()
                    .contains("\"onlineDevices\":2"));
        });
        verify(presenceService).subjectOnlineCounts(42, Set.of("first", "second"));
    }

    private MvcResult connectSubject(String presenceId) throws Exception {
        return mvc.perform(get("/api/v1/presence/subjects/42/online-count")
                        .param("presenceId", presenceId))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private static OnlineCountVo count(int devices) {
        return new OnlineCountVo(devices, devices, devices, 0);
    }
}
