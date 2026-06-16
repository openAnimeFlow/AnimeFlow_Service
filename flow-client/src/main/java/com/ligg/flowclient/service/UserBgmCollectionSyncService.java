package com.ligg.flowclient.service;

import com.ligg.flowclient.module.vo.UserBgmCollectionSyncStatusVo;

public interface UserBgmCollectionSyncService {

    /**
     * 提交异步同步任务，立即返回当前任务状态。
     */
    UserBgmCollectionSyncStatusVo triggerSync(Long userId, int subjectType);

    /**
     * 查询当前用户最近一次同步任务状态。
     */
    UserBgmCollectionSyncStatusVo getSyncStatus(Long userId);
}
