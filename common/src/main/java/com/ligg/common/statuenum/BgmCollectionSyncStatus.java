package com.ligg.common.statuenum;

/**
 * Bangumi 收藏同步任务状态。
 */
public enum BgmCollectionSyncStatus {
    IDLE,
    QUEUED,
    RUNNING,
    WAITING_CONFLICT,
    PARTIAL_FAILED,
    SUCCESS,
    FAILED,
    CANCELLED
}
