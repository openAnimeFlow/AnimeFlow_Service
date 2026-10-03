package com.ligg.flowclient.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ligg.common.entity.CollectionSyncConflictEntity;
import com.ligg.common.entity.CollectionSyncTaskEntity;
import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.statuenum.BgmCollectionSyncStatus;
import com.ligg.common.statuenum.CollectionSyncItemStatus;
import com.ligg.common.statuenum.CollectionSyncOperation;
import com.ligg.flowclient.mapper.CollectionSyncConflictMapper;
import com.ligg.flowclient.mapper.CollectionSyncTaskMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionSyncTaskServiceTest {

    private final CollectionSyncTaskMapper tasks = mock(CollectionSyncTaskMapper.class);
    private final CollectionSyncConflictMapper items = mock(CollectionSyncConflictMapper.class);
    private final BangumiOAuthTokenService tokens = mock(BangumiOAuthTokenService.class);
    private final CollectionSyncEvents events = mock(CollectionSyncEvents.class);
    private final CollectionSyncTaskService service = new CollectionSyncTaskService(
            tasks, items, mock(CollectionWriteLock.class), tokens, events);

    @Test
    void dismissItemsForCancelledSubjectClosesOpenItemsAndResummarizesActiveTasks() {
        var conflict = openItem(5L, 1L, CollectionSyncItemStatus.CONFLICT);
        var failed = openItem(6L, 2L, CollectionSyncItemStatus.FAILED);
        when(items.selectList(any())).thenReturn(List.of(conflict, failed)).thenReturn(List.of());
        when(tasks.selectById(1L)).thenReturn(task(1L, BgmCollectionSyncStatus.WAITING_CONFLICT));
        when(tasks.selectById(2L)).thenReturn(task(2L, BgmCollectionSyncStatus.SUCCESS));
        when(tokens.findBangumiOauth(10L)).thenReturn(oauth());

        service.dismissItemsForCancelledSubject(10L, 42);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<LambdaUpdateWrapper> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(items, times(2)).update(isNull(), captor.capture());
        for (LambdaUpdateWrapper<?> wrapper : captor.getAllValues()) {
            assertTrue(wrapper.getParamNameValuePairs().containsValue(CollectionSyncItemStatus.DONE));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(CollectionSyncOperation.UNCHANGED));
        }
        // 只有仍活动的任务会重新汇总，终态任务跳过。
        verify(tasks, times(1)).updateWithStatusVersion(any());
        verify(events, times(1)).changed(10L);
    }

    @Test
    void dismissItemsForCancelledSubjectIsNoOpWithoutOpenItems() {
        when(items.selectList(any())).thenReturn(List.of());

        service.dismissItemsForCancelledSubject(10L, 42);

        verify(items, never()).update(any(), any());
        verify(tasks, never()).updateWithStatusVersion(any());
        verify(events, never()).changed(any());
    }

    private static CollectionSyncConflictEntity openItem(long id, long taskId, CollectionSyncItemStatus status) {
        var item = new CollectionSyncConflictEntity();
        item.setId(id);
        item.setTaskId(taskId);
        item.setUserId(10L);
        item.setSubjectId(42);
        item.setStatus(status);
        item.setDesiredPayload("{\"type\":3}");
        item.setSelectedType(3);
        item.setErrorCode("STALE_CONFLICT");
        return item;
    }

    private static CollectionSyncTaskEntity task(long id, BgmCollectionSyncStatus status) {
        var task = new CollectionSyncTaskEntity();
        task.setId(id);
        task.setUserId(10L);
        task.setSubjectType(2);
        task.setOauthId(8L);
        task.setBgmAccountUid(99L);
        task.setStatus(status);
        task.setStatusVersion(1L);
        return task;
    }

    private static UserOauthEntity oauth() {
        var oauth = new UserOauthEntity();
        oauth.setId(8L);
        oauth.setPlatformUid(99L);
        return oauth;
    }
}
