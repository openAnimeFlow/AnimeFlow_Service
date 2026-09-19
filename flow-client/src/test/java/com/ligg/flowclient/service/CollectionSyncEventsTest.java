package com.ligg.flowclient.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.*;

class CollectionSyncEventsTest {
    @Test void notifiesAfterCommitButNotOnRollback() {
        var publisher = mock(ApplicationEventPublisher.class);
        var events = new CollectionSyncEvents(publisher);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            events.changed(10L);
            verifyNoInteractions(publisher);
            var callbacks = TransactionSynchronizationManager.getSynchronizations();
            callbacks.forEach(TransactionSynchronization::afterCommit);
            verify(publisher).publishEvent(new CollectionSyncEvents.Changed(10L));
        } finally { TransactionSynchronizationManager.clear(); }
        reset(publisher);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            events.changed(10L);
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(publisher);
        } finally { TransactionSynchronizationManager.clear(); }
    }
}
