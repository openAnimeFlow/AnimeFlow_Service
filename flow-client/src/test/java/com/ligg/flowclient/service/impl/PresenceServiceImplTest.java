package com.ligg.flowclient.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.constants.Constants;
import com.ligg.common.entity.BangumiSubjectEntity;
import com.ligg.flowclient.mapper.BangumiSubjectMapper;
import com.ligg.flowclient.module.dto.PresenceHeartbeatDto;
import com.ligg.flowclient.module.vo.PresenceContext;
import com.ligg.flowclient.service.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
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
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zset);
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

        BangumiSubjectEntity subject = new BangumiSubjectEntity();
        subject.setId(SUBJECT_ID);
        subject.setName("Anime");
        subject.setImages("{\"large\":\"https://example.com/cover.jpg\"}");
        when(zset.reverseRangeByScore(eq(Constants.PRESENCE_WATCHING_SUBJECTS_KEY),
                anyDouble(), anyDouble())).thenAnswer(invocation -> {
                    double min = invocation.getArgument(1);
                    double max = invocation.getArgument(2);
                    // Spring Data takes min, max even for reverse queries.
                    assertEquals(179.0, max - min, "查询范围应覆盖最近 180 秒且下限小于上限");
                    return Set.of(String.valueOf(SUBJECT_ID));
                });
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
}
