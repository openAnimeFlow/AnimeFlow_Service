package com.ligg.common.statuenum;

/** 持久化同步任务的执行阶段。数据库仍保存枚举名称以兼容既有任务数据。 */
public enum CollectionSyncPhase {
    SCANNING,
    APPLYING,
    RESOLVING
}
