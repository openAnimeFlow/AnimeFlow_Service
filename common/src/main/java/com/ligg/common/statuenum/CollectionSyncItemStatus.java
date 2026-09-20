package com.ligg.common.statuenum;

/** 用户收藏同步明细的持久化状态。 */
public enum CollectionSyncItemStatus {
    PLANNED,
    CONFLICT,
    RESOLUTION_PENDING,
    APPLY_PENDING,
    DONE,
    FAILED
}
