package com.ligg.flowclient.module.vo;

import com.ligg.common.statuenum.BgmCollectionSyncStatus;
import lombok.Data;

@Data
public class UserBgmCollectionSyncStatusVo {

    private BgmCollectionSyncStatus status;

    private Long userId;

    private Integer syncedCount;

    /** 扫描 Bangumi 分页时已发现的条目数；执行阶段由 syncedCount 表示进度。 */
    private Integer scannedCount;

    private Integer totalCount;

    private String message;

    private Long startedAt;

    private Long finishedAt;

    private Long taskId;
    private String phase;
    private Integer importedCount;
    private Integer uploadedCount;
    private Integer unchangedCount;
    private Integer pendingConflictCount;
    private Integer failedCount;
    private Long statusVersion;
}
