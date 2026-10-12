package com.ligg.flowclient.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.entity.UserBgmCollectionEntity;
import com.ligg.common.model.CoverImages;
import com.ligg.common.statuenum.CollectionRemoteSyncStatus;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.module.dto.UserBgmCollectionRow;
import com.ligg.flowclient.module.enums.CollectionSort;
import com.ligg.flowclient.module.vo.CollectionUpdateVo;
import com.ligg.flowclient.service.BangumiOAuthTokenService;
import com.ligg.flowclient.service.CollectionUploadService;
import com.ligg.flowclient.service.CollectionWriteLock;
import com.ligg.flowclient.service.CollectionSyncTaskService;
import com.ligg.flowclient.service.ImageBackfillService;
import com.ligg.flowclient.service.LocalCollectionWriter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserBgmCollectionServiceImplTest {

    private final UserBgmCollectionMapper mapper = mock(UserBgmCollectionMapper.class);
    private final ImageBackfillService imageBackfillService = mock(ImageBackfillService.class);
    private final CollectionWriteLock writeLock = mock(CollectionWriteLock.class);
    private final LocalCollectionWriter localWriter = mock(LocalCollectionWriter.class);
    private final CollectionSyncTaskService collectionSyncTaskService = mock(CollectionSyncTaskService.class);
    private final UserBgmCollectionServiceImpl service = new UserBgmCollectionServiceImpl(
            mapper,
            new ObjectMapper(),
            writeLock,
            localWriter,
            mock(CollectionUploadService.class),
            mock(BangumiOAuthTokenService.class),
            imageBackfillService,
            collectionSyncTaskService);

    @Test
    void watchSortPassesEnumAndNormalizedKeywordWithoutChangingCountQuery() {
        when(mapper.countByUserFilter(7L, 3, 2, "葬送")).thenReturn(2L);
        when(mapper.selectPageByUserFilter(7L, 3, 2, "葬送", CollectionSort.WATCH, 5, 1)).thenReturn(List.of());

        var result = service.listMyCollections(null, 7L, 2, 3, " 葬送 ", CollectionSort.WATCH, 5, 1);

        assertEquals(2, result.getTotal());
        verify(mapper).countByUserFilter(7L, 3, 2, "葬送");
        verify(mapper).selectPageByUserFilter(7L, 3, 2, "葬送", CollectionSort.WATCH, 5, 1);
    }

    @Test
    void defaultSortDoesNotRequestWatchJoin() {
        when(mapper.countByUserFilter(7L, 3, 2, null)).thenReturn(1L);
        when(mapper.selectPageByUserFilter(7L, 3, 2, null, CollectionSort.DEFAULT, 20, 0)).thenReturn(List.of());

        service.listMyCollections(null, 7L, 2, 3, null, CollectionSort.DEFAULT, 20, 0);

        verify(mapper).selectPageByUserFilter(7L, 3, 2, null, CollectionSort.DEFAULT, 20, 0);
    }

    @Test
    void deleteCollectionCancelsUnderWriteLockAndReportsLocalOnly() {
        var cancelled = new UserBgmCollectionEntity();
        cancelled.setType(UserBgmCollectionEntity.TYPE_CANCELLED);
        cancelled.setVersion(3L);
        cancelled.setRemoteSyncStatus(CollectionRemoteSyncStatus.LOCAL_ONLY);
        when(writeLock.execute(anyLong(), anyInt(), any())).thenAnswer(i -> ((Supplier<?>) i.getArgument(2)).get());
        when(localWriter.cancel(10L, 42)).thenReturn(cancelled);

        var result = service.deleteCollection(10L, 42);

        assertEquals(new CollectionUpdateVo(true, CollectionRemoteSyncStatus.LOCAL_ONLY, 3L), result);
        verify(localWriter).cancel(10L, 42);
        verify(collectionSyncTaskService).dismissItemsForCancelledSubject(10L, 42);
    }

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
        when(mapper.selectPageByUserFilter(anyLong(), anyInt(), anyInt(), any(), any(CollectionSort.class), anyInt(), anyInt()))
                .thenReturn(List.of(row));
        when(imageBackfillService.resolve(any(), anyInt(), any())).thenReturn(new CoverImages());

        var vo = service.listMyCollections(null, 7L, 2, 3, null, CollectionSort.DEFAULT, 20, 0);

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
        when(mapper.selectPageByUserFilter(anyLong(), anyInt(), anyInt(), any(), any(CollectionSort.class), anyInt(), anyInt()))
                .thenReturn(List.of(row));
        when(imageBackfillService.resolve(any(), anyInt(), any())).thenReturn(new CoverImages());

        var item = service.listMyCollections(null, 7L, 2, 3, null, CollectionSort.DEFAULT, 20, 0).getData().get(0);

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
        when(mapper.selectPageByUserFilter(anyLong(), anyInt(), anyInt(), any(), any(CollectionSort.class), anyInt(), anyInt()))
                .thenReturn(List.of(row));
        when(imageBackfillService.resolve(any(), anyInt(), any())).thenReturn(new CoverImages());

        var item = service.listMyCollections(null, 7L, 2, 3, null, CollectionSort.DEFAULT, 20, 0).getData().get(0);

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
        when(mapper.selectPageByUserFilter(anyLong(), anyInt(), anyInt(), any(), any(CollectionSort.class), anyInt(), anyInt()))
                .thenReturn(List.of(row));
        when(imageBackfillService.resolve(any(), anyInt(), any())).thenReturn(new CoverImages());

        var item = service.listMyCollections(null, 7L, 2, 2, null, CollectionSort.DEFAULT, 20, 0).getData().get(0);

        assertEquals(0, item.getTotalEpisodes());
        assertEquals(0, item.getWatchedEpisode());
        assertNull(item.getNextEpisodeAirDate());
    }
}
