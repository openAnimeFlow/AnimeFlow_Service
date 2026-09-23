package com.ligg.flowclient.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.constants.Constants;
import com.ligg.common.entity.BangumiSubjectEntity;
import com.ligg.flowclient.mapper.BangumiSubjectMapper;
import com.ligg.flowclient.module.dto.PresenceHeartbeatDto;
import com.ligg.flowclient.module.vo.PresenceContext;
import com.ligg.flowclient.module.vo.WatchingSubjectVo;
import com.ligg.flowclient.service.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceServiceImplTest {

    private static final int SUBJECT_ID = 571784;
    private static final String SUBJECT_DEVICES = Constants.PRESENCE_SUBJECT_KEY + ":" + SUBJECT_ID + ":devices";
    private static final String SUBJECT_USERS = Constants.PRESENCE_SUBJECT_KEY + ":" + SUBJECT_ID + ":users";

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, Object> values;
    @Mock private ZSetOperations<String, String> zset;
    @Mock private JwtTokenService jwtTokenService;
    @Mock private BangumiSubjectMapper bangumiSubjectMapper;

    private PresenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PresenceServiceImpl(redisTemplate, stringRedisTemplate,
                jwtTokenService, bangumiSubjectMapper, new ObjectMapper());
        lenient().when(redisTemplate.opsForValue()).thenReturn(values);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zset);
        lenient().when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class), anyList(), any(), any()))
                .thenReturn(List.of(String.valueOf(SUBJECT_ID)));
    }

    @Test
    void pausedPlaybackRemainsInWatchingSubjects() {
        PresenceHeartbeatDto dto = new PresenceHeartbeatDto();
        dto.setVisitorId("visitor-1");
        dto.setClientType("ANDROID");
        dto.setStatus("paused");
        dto.setSubjectId(SUBJECT_ID);
        dto.setEpisodeId(1699260);

        service.heartbeat("presence-1", dto, null, false);

        verify(zset).add(eq(SUBJECT_DEVICES), eq("presence-1"), anyDouble());
        verify(zset).add(eq(Constants.PRESENCE_WATCHING_SUBJECTS_KEY), eq(String.valueOf(SUBJECT_ID)), anyDouble());
        verify(stringRedisTemplate).convertAndSend(
                Constants.PRESENCE_CHANGED_CHANNEL, "changed");

        BangumiSubjectEntity subject = new BangumiSubjectEntity();
        subject.setId(SUBJECT_ID);
        subject.setName("Anime");
        subject.setImages("{\"large\":\"https://example.com/cover.jpg\"}");
        when(bangumiSubjectMapper.selectByIds(List.of(SUBJECT_ID))).thenReturn(List.of(subject));
        when(zset.rangeByScore(eq(SUBJECT_USERS), anyDouble(), anyDouble()))
                .thenReturn(Set.of("visitor:visitor-1"));
        when(zset.zCard(SUBJECT_DEVICES)).thenReturn(1L);

        var result = service.watchingSubjects();

        assertEquals(1, result.size());
        assertEquals(SUBJECT_ID, result.get(0).getSubjectId());
        assertEquals(1, result.get(0).getOnline().getOnlineUsers());
        assertEquals("https://wsrv.nl/?url=https://example.com/cover.jpg",
                result.get(0).getImages().getLarge());
    }

    @Test
    void unchangedHeartbeatDoesNotBroadcastAnotherSnapshot() {
        long now = java.time.Instant.now().getEpochSecond();
        PresenceContext previous = new PresenceContext(
                "presence-1", "visitor-1", null, "visitor:visitor-1", "ANDROID", null,
                "watching", SUBJECT_ID, 1699260, 120, now - 1);
        when(values.get(Constants.PRESENCE_KEY + ":presence-1")).thenReturn(previous);

        PresenceHeartbeatDto dto = new PresenceHeartbeatDto();
        dto.setVisitorId("visitor-1");
        dto.setClientType("ANDROID");
        dto.setStatus("watching");
        dto.setSubjectId(SUBJECT_ID);
        dto.setEpisodeId(1699260);
        dto.setPositionSeconds(121);

        service.heartbeat("presence-1", dto, null, false);

        verify(stringRedisTemplate, never()).convertAndSend(
                Constants.PRESENCE_CHANGED_CHANNEL, "changed");
    }

    @Test
    void loggingInRemovesOldVisitorFromGlobalUserIndex() {
        long now = java.time.Instant.now().getEpochSecond();
        PresenceContext previous = new PresenceContext(
                "presence-1", "visitor-1", null, "visitor:visitor-1", "ANDROID", null,
                "online", null, null, null, now);
        when(values.get(Constants.PRESENCE_KEY + ":presence-1")).thenReturn(previous);
        when(jwtTokenService.validateAccessToken("access-token")).thenReturn(42L);
        when(zset.rangeByScore(eq(Constants.PRESENCE_DEVICES_KEY), anyDouble(), anyDouble()))
                .thenReturn(Set.of("presence-1"));

        PresenceHeartbeatDto dto = new PresenceHeartbeatDto();
        dto.setVisitorId("visitor-1");
        dto.setClientType("ANDROID");
        dto.setStatus("online");

        service.heartbeat("presence-1", dto, "access-token", true);

        verify(zset).add(eq(Constants.PRESENCE_USERS_KEY), eq("user:42"), anyDouble());
        verify(zset).remove(Constants.PRESENCE_USERS_KEY, "visitor:visitor-1");
    }

    @Test
    void loggingInKeepsVisitorIndexWhenAnotherDeviceIsActive() {
        long now = java.time.Instant.now().getEpochSecond();
        PresenceContext previous = new PresenceContext(
                "presence-1", "visitor-1", null, "visitor:visitor-1", "ANDROID", null,
                "online", null, null, null, now);
        PresenceContext otherDevice = new PresenceContext(
                "presence-2", "visitor-1", null, "visitor:visitor-1", "ANDROID", null,
                "online", null, null, null, now);
        when(values.get(Constants.PRESENCE_KEY + ":presence-1")).thenReturn(previous);
        when(values.get(Constants.PRESENCE_KEY + ":presence-2")).thenReturn(otherDevice);
        when(jwtTokenService.validateAccessToken("access-token")).thenReturn(42L);
        when(zset.rangeByScore(eq(Constants.PRESENCE_DEVICES_KEY), anyDouble(), anyDouble()))
                .thenReturn(Set.of("presence-1", "presence-2"));

        PresenceHeartbeatDto dto = new PresenceHeartbeatDto();
        dto.setVisitorId("visitor-1");
        dto.setClientType("ANDROID");
        dto.setStatus("online");

        service.heartbeat("presence-1", dto, "access-token", true);

        verify(zset, never()).remove(Constants.PRESENCE_USERS_KEY, "visitor:visitor-1");
    }

    @Test
    void watchingSubjectsReturnsTopOneHundredByOnlineUsers() {
        List<Integer> ids = IntStream.rangeClosed(1, 101).boxed().toList();
        List<BangumiSubjectEntity> subjects = ids.stream().map(id -> {
            BangumiSubjectEntity subject = new BangumiSubjectEntity();
            subject.setId(id);
            subject.setName("Anime " + id);
            return subject;
        }).toList();

        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class), anyList(), any(), any()))
                .thenReturn(ids.stream().map(String::valueOf).toList());
        when(bangumiSubjectMapper.selectByIds(anyList())).thenReturn(subjects);
        when(zset.rangeByScore(anyString(), anyDouble(), anyDouble())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String[] keyParts = key.split(":");
            int subjectId = Integer.parseInt(keyParts[keyParts.length - 2]);
            Set<String> users = new HashSet<>();
            for (int index = 0; index < 102 - subjectId; index++) {
                users.add("visitor:" + subjectId + ":" + index);
            }
            return users;
        });
        when(zset.zCard(anyString())).thenReturn(1L);

        List<WatchingSubjectVo> result = service.watchingSubjects();

        assertEquals(100, result.size());
        assertEquals(1, result.get(0).getSubjectId());
        assertEquals(100, result.get(99).getSubjectId());

        ArgumentCaptor<List<Integer>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(bangumiSubjectMapper).selectByIds(idsCaptor.capture());
        assertEquals(100, idsCaptor.getValue().size());
        assertEquals(1, idsCaptor.getValue().get(0));
        assertEquals(100, idsCaptor.getValue().get(99));
    }

    @Test
    void leavingPlaybackRemovesPausedSubject() {
        PresenceContext previous = new PresenceContext("presence-1", "visitor-1", null,
                "visitor:visitor-1", "ANDROID", null, "paused", SUBJECT_ID,
                1699260, 120, 1L);
        when(values.get(Constants.PRESENCE_KEY + ":presence-1")).thenReturn(previous);

        PresenceHeartbeatDto dto = new PresenceHeartbeatDto();
        dto.setVisitorId("visitor-1");
        dto.setClientType("ANDROID");
        dto.setStatus("online");
        service.heartbeat("presence-1", dto, null, false);

        verify(zset).remove(SUBJECT_DEVICES, "presence-1");
        verify(zset).remove(Constants.PRESENCE_WATCHING_SUBJECTS_KEY, String.valueOf(SUBJECT_ID));
    }

    @Test
    void offlineRemovesSubjectAndGlobalUserIndexes() {
        long now = java.time.Instant.now().getEpochSecond();
        PresenceContext previous = new PresenceContext(
                "presence-1", "visitor-1", null, "visitor:visitor-1", "ANDROID", null,
                "watching", SUBJECT_ID, 1699260, 120, now);
        when(values.get(Constants.PRESENCE_KEY + ":presence-1")).thenReturn(previous);

        service.offline("presence-1");

        verify(zset).remove(Constants.PRESENCE_DEVICES_KEY, "presence-1");
        verify(zset).remove(Constants.PRESENCE_USERS_KEY, "visitor:visitor-1");
        verify(zset).remove(SUBJECT_DEVICES, "presence-1");
        verify(zset).remove(SUBJECT_USERS, "visitor:visitor-1");
    }

    @Test
    void switchingSubjectRemovesPreviousSubjectUserIndex() {
        long now = java.time.Instant.now().getEpochSecond();
        int nextSubjectId = SUBJECT_ID + 1;
        String nextSubjectDevices = Constants.PRESENCE_SUBJECT_KEY + ":" + nextSubjectId + ":devices";
        String nextSubjectUsers = Constants.PRESENCE_SUBJECT_KEY + ":" + nextSubjectId + ":users";
        PresenceContext previous = new PresenceContext(
                "presence-1", "visitor-1", null, "visitor:visitor-1", "ANDROID", null,
                "watching", SUBJECT_ID, 1699260, 120, now);
        when(values.get(Constants.PRESENCE_KEY + ":presence-1")).thenReturn(previous);

        PresenceHeartbeatDto dto = new PresenceHeartbeatDto();
        dto.setVisitorId("visitor-1");
        dto.setClientType("ANDROID");
        dto.setStatus("watching");
        dto.setSubjectId(nextSubjectId);
        dto.setEpisodeId(1699261);

        service.heartbeat("presence-1", dto, null, false);

        verify(zset).remove(SUBJECT_DEVICES, "presence-1");
        verify(zset).remove(SUBJECT_USERS, "visitor:visitor-1");
        verify(zset).add(eq(nextSubjectDevices), eq("presence-1"), anyDouble());
        verify(zset).add(eq(nextSubjectUsers), eq("visitor:visitor-1"), anyDouble());
    }
}
