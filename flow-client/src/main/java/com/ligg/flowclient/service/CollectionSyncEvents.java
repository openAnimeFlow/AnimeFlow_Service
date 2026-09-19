package com.ligg.flowclient.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Notify only after committed writes; notifications never carry collection payloads. */
@Component
@RequiredArgsConstructor
public class CollectionSyncEvents {
    public record Changed(Long userId) {}
    private final ApplicationEventPublisher publisher;

    public void changed(Long userId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish(userId); }
            });
        } else {
            publish(userId);
        }
    }

    private void publish(Long userId) {
        publisher.publishEvent(new Changed(userId));
    }
}
