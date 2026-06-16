package com.ligg.flowclient.module.vo;

import com.ligg.common.statuenum.BgmCollectionSyncStatus;
import lombok.Data;

@Data
public class UserBgmCollectionSyncStatusVo {

    private BgmCollectionSyncStatus status;

    private Long userId;

    private Integer syncedCount;

    private Integer totalCount;

    private String message;

    private Long startedAt;

    private Long finishedAt;
}
