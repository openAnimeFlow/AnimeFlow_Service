package com.ligg.flowscheduler.service;

import com.ligg.flowscheduler.config.CollectionSyncCleanupProperties;
import com.ligg.flowscheduler.mapper.CollectionSyncCleanupMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CollectionSyncCleanupServiceTest {
    private final CollectionSyncCleanupMapper tasks = mock(CollectionSyncCleanupMapper.class);
    private final CollectionSyncCleanupProperties properties = new CollectionSyncCleanupProperties();
    private final CollectionSyncCleanupService service = new CollectionSyncCleanupService(tasks, properties);

    @Test
    void deletesItemsBeforeTheirExpiredTask() {
        properties.setRetentionDays(30);
        properties.setBatchSize(2);
        when(tasks.selectExpiredTaskIds(any(LocalDateTime.class), eq(2)))
                .thenReturn(List.of(11L, 12L));
        when(tasks.deleteItemsByTaskId(anyLong())).thenReturn(3);
        when(tasks.deleteExpiredTask(anyLong(), any(LocalDateTime.class))).thenReturn(1);

        service.cleanupExpiredTasks();

        var order = inOrder(tasks);
        order.verify(tasks).selectExpiredTaskIds(any(LocalDateTime.class), eq(2));
        order.verify(tasks).deleteItemsByTaskId(11L);
        order.verify(tasks).deleteExpiredTask(eq(11L), any(LocalDateTime.class));
        order.verify(tasks).deleteItemsByTaskId(12L);
        order.verify(tasks).deleteExpiredTask(eq(12L), any(LocalDateTime.class));
        verifyNoMoreInteractions(tasks);
    }

    @Test
    void disabledCleanupDoesNotQueryDatabase() {
        properties.setEnabled(false);

        service.cleanupExpiredTasks();

        verifyNoInteractions(tasks);
    }
}
