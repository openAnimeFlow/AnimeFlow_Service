package com.ligg.flowclient.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.model.CoverImages;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.module.dto.UserBgmCollectionRow;
import com.ligg.flowclient.service.BangumiOAuthTokenService;
import com.ligg.flowclient.service.CollectionUploadService;
import com.ligg.flowclient.service.CollectionWriteLock;
import com.ligg.flowclient.service.ImageBackfillService;
import com.ligg.flowclient.service.LocalCollectionWriter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserBgmCollectionServiceImplTest {

    private final UserBgmCollectionMapper mapper = mock(UserBgmCollectionMapper.class);
    private final ImageBackfillService imageBackfillService = mock(ImageBackfillService.class);
    private final UserBgmCollectionServiceImpl service = new UserBgmCollectionServiceImpl(
            mapper,
            new ObjectMapper(),
            mock(CollectionWriteLock.class),
            mock(LocalCollectionWriter.class),
            mock(CollectionUploadService.class),
            mock(BangumiOAuthTokenService.class),
            imageBackfillService);

    @Test
    void returnsEpisodeTotalWatchProgressAndNextAirDate() {
        UserBgmCollectionRow row = new UserBgmCollectionRow();
        row.setSubjectId(41);
        row.setSubjectType(2);
        row.setType(3);
        row.setTotalEpisodes(12);
        row.setWatchedEpisode(5);
        row.setNextEpisodeAirDate("2026-10-11");
        when(mapper.countByUserFilter(anyLong(), anyInt(), anyInt(), any())).thenReturn(1L);
        when(mapper.selectPageByUserFilter(anyLong(), anyInt(), anyInt(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row));
        when(imageBackfillService.resolve(any(), anyInt(), any())).thenReturn(new CoverImages());

        var vo = service.listMyCollections(null, 7L, 2, 3, null, 20, 0);

        assertEquals(1, vo.getTotal());
        var item = vo.getData().get(0);
        assertEquals(12, item.getTotalEpisodes());
        assertEquals(5, item.getWatchedEpisode());
        assertEquals("2026-10-11", item.getNextEpisodeAirDate());
    }

    @Test
    void capsWatchedEpisodeAtTotalEpisodes() {
        UserBgmCollectionRow row = new UserBgmCollectionRow();
        row.setSubjectId(43);
        row.setSubjectType(2);
        row.setType(3);
        row.setTotalEpisodes(13);
        row.setWatchedEpisode(15); // Bangumi 的 ep_status 可能把特别篇也算进去
        when(mapper.countByUserFilter(anyLong(), anyInt(), anyInt(), any())).thenReturn(1L);
        when(mapper.selectPageByUserFilter(anyLong(), anyInt(), anyInt(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row));
        when(imageBackfillService.resolve(any(), anyInt(), any())).thenReturn(new CoverImages());

        var item = service.listMyCollections(null, 7L, 2, 3, null, 20, 0).getData().get(0);

        assertEquals(13, item.getTotalEpisodes());
        assertEquals(13, item.getWatchedEpisode());
    }

    @Test
    void keepsWatchedEpisodeWhenArchiveHasNoEpisodes() {
        UserBgmCollectionRow row = new UserBgmCollectionRow();
        row.setSubjectId(44);
        row.setSubjectType(2);
        row.setType(3);
        row.setTotalEpisodes(0);
        row.setWatchedEpisode(4);
        when(mapper.countByUserFilter(anyLong(), anyInt(), anyInt(), any())).thenReturn(1L);
        when(mapper.selectPageByUserFilter(anyLong(), anyInt(), anyInt(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row));
        when(imageBackfillService.resolve(any(), anyInt(), any())).thenReturn(new CoverImages());

        var item = service.listMyCollections(null, 7L, 2, 3, null, 20, 0).getData().get(0);

        assertEquals(0, item.getTotalEpisodes());
        assertEquals(4, item.getWatchedEpisode());
    }

    @Test
    void fallsBackToZeroEpisodesWhenRowHasNoEpisodeData() {
        UserBgmCollectionRow row = new UserBgmCollectionRow();
        row.setSubjectId(42);
        row.setSubjectType(2);
        row.setType(2);
        when(mapper.countByUserFilter(anyLong(), anyInt(), anyInt(), any())).thenReturn(1L);
        when(mapper.selectPageByUserFilter(anyLong(), anyInt(), anyInt(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row));
        when(imageBackfillService.resolve(any(), anyInt(), any())).thenReturn(new CoverImages());

        var item = service.listMyCollections(null, 7L, 2, 2, null, 20, 0).getData().get(0);

        assertEquals(0, item.getTotalEpisodes());
        assertEquals(0, item.getWatchedEpisode());
        assertNull(item.getNextEpisodeAirDate());
    }
}
