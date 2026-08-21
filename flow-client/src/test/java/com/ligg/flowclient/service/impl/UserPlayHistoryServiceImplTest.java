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
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void save_keepsAtMostFiftyRecordsPerUser() {
        UserPlayHistoryServiceImpl service = new UserPlayHistoryServiceImpl(
                jwtTokenService, bangumiEpisodeMapper, userPlayHistoryMapper, new ObjectMapper());
        BangumiEpisodeEntity episode = new BangumiEpisodeEntity();
        episode.setId(200);
        episode.setSubjectId(1);
        episode.setSort(1);
        when(jwtTokenService.validateAccessToken("access-token")).thenReturn(10L);
        when(bangumiEpisodeMapper.selectById(200)).thenReturn(episode);

        service.save("access-token", dto());

        verify(userPlayHistoryMapper).upsert(any(UserPlayHistoryEntity.class),
                eq(PlayHistorySaveEventType.DEFAULT));
        verify(userPlayHistoryMapper).deleteExceedingByUser(10L, 50);
    }

    private static SavePlayHistoryDto dto() {
        SavePlayHistoryDto dto = new SavePlayHistoryDto();
        dto.setSubjectId(1);
        dto.setEpisodeId(200);
        dto.setEpisodeSort(1);
        dto.setSubjectName("葬送的芙莉莲");
        dto.setCover("https://example.com/cover.jpg");
        dto.setAlias(List.of("Frieren"));
        dto.setPositionSeconds(10);
        dto.setDurationSeconds(120);
        return dto;
    }
}
