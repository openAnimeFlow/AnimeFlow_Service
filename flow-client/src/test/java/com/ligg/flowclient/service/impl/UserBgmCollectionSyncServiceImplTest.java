package com.ligg.flowclient.service.impl;

import com.ligg.common.statuenum.BgmCollectionSyncStatus;
import com.ligg.common.entity.CollectionSyncTaskEntity;
import com.ligg.flowclient.module.vo.UserBgmCollectionSyncStatusVo;
import com.ligg.flowclient.service.CollectionSyncItemExecutor;
import com.ligg.flowclient.service.CollectionSyncTaskService;
import com.ligg.flowclient.service.UserBgmCollectionSyncRunner;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class UserBgmCollectionSyncServiceImplTest {

    private final CollectionSyncTaskService tasks = mock(CollectionSyncTaskService.class);
    private final UserBgmCollectionSyncRunner runner = mock(UserBgmCollectionSyncRunner.class);
    private final CollectionSyncItemExecutor executor = mock(CollectionSyncItemExecutor.class);
    private final UserBgmCollectionSyncServiceImpl service =
            new UserBgmCollectionSyncServiceImpl(tasks, runner, executor);

    @Test
    void successfulSyncCannotBeTriggeredAgainDuringCooldown() {
        var task = new CollectionSyncTaskEntity();
        task.setId(42L);
        task.setStatus(BgmCollectionSyncStatus.SUCCESS);
        task.setFinishedAt(LocalDateTime.now().minusMinutes(1));
        var status = new UserBgmCollectionSyncStatusVo();
        status.setStatus(BgmCollectionSyncStatus.SUCCESS);

        when(tasks.createOrGet(7L, 2, "request-1")).thenReturn(task);
        when(tasks.isWithinCooldown(task)).thenReturn(true);
        when(tasks.toStatus(task)).thenReturn(status);

        var result = service.triggerSync(7L, 2, "request-1");

        assertEquals("同步过于频繁，请稍后再试", result.getMessage());
        verify(tasks).toStatus(task);
        verifyNoInteractions(runner);
    }

    @Test
    void syncDispatchesAfterCooldownExpires() {
        var task = new CollectionSyncTaskEntity();
        task.setId(42L);
        var status = new UserBgmCollectionSyncStatusVo();

        when(tasks.createOrGet(7L, 2, "request-2")).thenReturn(task);
        when(tasks.isWithinCooldown(task)).thenReturn(false);
        when(tasks.toStatus(task)).thenReturn(status);

        assertEquals(status, service.triggerSync(7L, 2, "request-2"));

        verify(runner).runTask(42L);
        verify(tasks).toStatus(task);
    }

    @Test
    void cooldownOnlyAppliesToRecentSuccessfulTask() {
        var taskService = new CollectionSyncTaskService(null, null, null, null, null);
        var task = new CollectionSyncTaskEntity();
        task.setStatus(BgmCollectionSyncStatus.SUCCESS);
        task.setFinishedAt(LocalDateTime.now().minusMinutes(59));
        org.junit.jupiter.api.Assertions.assertTrue(taskService.isWithinCooldown(task));

        task.setFinishedAt(LocalDateTime.now().minusMinutes(61));
        org.junit.jupiter.api.Assertions.assertFalse(taskService.isWithinCooldown(task));
        task.setStatus(BgmCollectionSyncStatus.PARTIAL_FAILED);
        org.junit.jupiter.api.Assertions.assertFalse(taskService.isWithinCooldown(task));
    }
}
