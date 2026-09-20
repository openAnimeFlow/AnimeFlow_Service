package com.ligg.flowclient.module.vo;

import com.ligg.common.statuenum.CollectionRemoteSyncStatus;

public record CollectionUpdateVo(boolean localSaved, CollectionRemoteSyncStatus remoteSyncStatus,
                                 long localVersion) {
}
