package com.ligg.flowclient.service;

import com.ligg.flowclient.module.dto.CollectionConflictResolveDto;
import com.ligg.flowclient.service.impl.UserBgmCollectionSyncServiceImpl;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollectionConflictDispatchTest {
    final CollectionSyncTaskService tasks = mock(CollectionSyncTaskService.class);
    final UserBgmCollectionSyncRunner runner = mock(UserBgmCollectionSyncRunner.class);
    final CollectionSyncItemExecutor executor = mock(CollectionSyncItemExecutor.class);
    final UserBgmCollectionSyncServiceImpl service = new UserBgmCollectionSyncServiceImpl(tasks, runner, executor);

    private CollectionConflictResolveDto.Item decision(long id) {
        var item = new CollectionConflictResolveDto.Item();
        item.setConflictId(id); item.setConflictVersion(1L); item.setSelectedType(3);
        return item;
    }

    @Test void dispatchesOnceAfterAllDecisionsArePersisted() {
        service.resolveConflicts(10L, 2L, List.of(decision(1), decision(2)));
        var order = inOrder(executor, runner);
        order.verify(executor).resolve(10L, 2L, 1L, 1L, 3);
        order.verify(executor).resolve(10L, 2L, 2L, 1L, 3);
        order.verify(runner).runTask(2L);
        order.verifyNoMoreInteractions();
    }

    @Test void laterStaleDecisionStillDispatchesEarlierAcceptedDecision() {
        doThrow(new IllegalArgumentException("STALE_CONFLICT")).when(executor).resolve(10L, 2L, 2L, 1L, 3);
        assertThrows(IllegalArgumentException.class,
                () -> service.resolveConflicts(10L, 2L, List.of(decision(1), decision(2))));
        verify(runner).runTask(2L);
    }

    @Test void rejectedFirstDecisionDoesNotDispatch() {
        doThrow(new IllegalArgumentException("STALE_CONFLICT")).when(executor).resolve(10L, 2L, 1L, 1L, 3);
        assertThrows(IllegalArgumentException.class,
                () -> service.resolveConflicts(10L, 2L, List.of(decision(1))));
        verifyNoInteractions(runner);
    }

    @Test void fullAsyncQueueLeavesPersistedDecisionForRecovery() {
        doThrow(new RejectedExecutionException()).when(runner).runTask(2L);
        assertDoesNotThrow(() -> service.resolveConflicts(10L, 2L, List.of(decision(1))));
        verify(executor).resolve(10L, 2L, 1L, 1L, 3);
    }
}
