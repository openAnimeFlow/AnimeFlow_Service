package com.ligg.flowclient.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.entity.BangumiEpisodeEntity;
import com.ligg.common.entity.UserPlayHistoryEntity;
import com.ligg.flowclient.mapper.BangumiEpisodeMapper;
import com.ligg.flowclient.mapper.UserPlayHistoryMapper;
import com.ligg.flowclient.module.dto.SavePlayHistoryDto;
import com.ligg.flowclient.module.enums.PlayHistorySaveEventType;
import com.ligg.flowclient.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPlayHistoryServiceImplTest {

    @org.mockito.Mock
    private JwtTokenService jwtTokenService;

    @org.mockito.Mock
    private BangumiEpisodeMapper bangumiEpisodeMapper;

    @org.mockito.Mock
    private UserPlayHistoryMapper userPlayHistoryMapper;

    @Test
    void save_mapsDtoFieldsAndDeletesExceedingRecords() {
        UserPlayHistoryServiceImpl service = service();
        BangumiEpisodeEntity episode = episode(200, 1);
        when(jwtTokenService.validateAccessToken("access-token")).thenReturn(10L);
        when(bangumiEpisodeMapper.selectById(200)).thenReturn(episode);

        service.save("access-token", dto());

        ArgumentCaptor<UserPlayHistoryEntity> captor = ArgumentCaptor.forClass(UserPlayHistoryEntity.class);
        verify(userPlayHistoryMapper).upsert(captor.capture(), eq(PlayHistorySaveEventType.DEFAULT));
        verify(userPlayHistoryMapper).deleteExceedingByUser(10L, 50);

        UserPlayHistoryEntity row = captor.getValue();
        assertEquals(10L, row.getUserId());
        assertEquals(1, row.getSubjectId());
        assertEquals(200, row.getEpisodeId());
        assertEquals(1, row.getEpisodeSort());
        assertEquals("葬送的芙莉莲", row.getSubjectName());
        assertEquals("https://example.com/cover.jpg", row.getCover());
        assertEquals("[\"Frieren\"]", row.getAlias());
        assertEquals(10, row.getPositionSeconds());
        assertEquals(120, row.getDurationSeconds());
    }

    @Test
    void save_throwsWhenEpisodeIsMissing() {
        UserPlayHistoryServiceImpl service = service();
        when(jwtTokenService.validateAccessToken("access-token")).thenReturn(10L);
        when(bangumiEpisodeMapper.selectById(200)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.save("access-token", dto()));

        verify(userPlayHistoryMapper, never()).upsert(any(UserPlayHistoryEntity.class),
                any(PlayHistorySaveEventType.class));
        verify(userPlayHistoryMapper, never()).deleteExceedingByUser(any(Long.class), any(Integer.class));
    }

    @Test
    void save_throwsWhenEpisodeBelongsToDifferentSubject() {
        UserPlayHistoryServiceImpl service = service();
        when(jwtTokenService.validateAccessToken("access-token")).thenReturn(10L);
        when(bangumiEpisodeMapper.selectById(200)).thenReturn(episode(200, 99));

        assertThrows(IllegalArgumentException.class, () -> service.save("access-token", dto()));

        verify(userPlayHistoryMapper, never()).upsert(any(UserPlayHistoryEntity.class),
                any(PlayHistorySaveEventType.class));
    }

    @Test
    void save_throwsWhenPositionExceedsDuration() {
        UserPlayHistoryServiceImpl service = service();
        SavePlayHistoryDto dto = dto();
        dto.setPositionSeconds(121);
        when(jwtTokenService.validateAccessToken("access-token")).thenReturn(10L);
        when(bangumiEpisodeMapper.selectById(200)).thenReturn(episode(200, 1));

        assertThrows(IllegalArgumentException.class, () -> service.save("access-token", dto));

        verify(userPlayHistoryMapper, never()).upsert(any(UserPlayHistoryEntity.class),
                any(PlayHistorySaveEventType.class));
    }

    private UserPlayHistoryServiceImpl service() {
        return new UserPlayHistoryServiceImpl(
                jwtTokenService, bangumiEpisodeMapper, userPlayHistoryMapper, new ObjectMapper());
    }

    private static BangumiEpisodeEntity episode(int id, int subjectId) {
        BangumiEpisodeEntity episode = new BangumiEpisodeEntity();
        episode.setId(id);
        episode.setSubjectId(subjectId);
        episode.setSort(1);
        return episode;
    }

    private static SavePlayHistoryDto dto() {
        SavePlayHistoryDto dto = new SavePlayHistoryDto();
        dto.setSubjectId(1);
        dto.setEpisodeId(200);
        dto.setEpisodeSort(1);
        dto.setSubjectName(" 葬送的芙莉莲 ");
        dto.setCover(" https://example.com/cover.jpg ");
        dto.setAlias(List.of("Frieren"));
        dto.setPositionSeconds(10);
        dto.setDurationSeconds(120);
        return dto;
    }
}
