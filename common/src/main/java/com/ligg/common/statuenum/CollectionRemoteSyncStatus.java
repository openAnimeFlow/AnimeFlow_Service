package com.ligg.common.statuenum;

/** Local persistence and remote synchronization are separate outcomes. */
public enum CollectionRemoteSyncStatus {
    LOCAL_ONLY, PENDING, SYNCED, AUTH_REQUIRED, CONFLICT
}
