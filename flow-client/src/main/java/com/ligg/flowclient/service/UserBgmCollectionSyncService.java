package com.ligg.flowclient.service;

import com.ligg.flowclient.module.vo.CollectionConflictVo;
import com.ligg.flowclient.module.vo.UserBgmCollectionSyncStatusVo;

import java.util.List;

public interface UserBgmCollectionSyncService {

    /**
     * 提交异步同步任务，立即返回当前任务状态。
     */
    UserBgmCollectionSyncStatusVo triggerSync(Long userId, int subjectType, String requestId);

    /**
     * 查询当前用户最近一次同步任务状态。
     */
    UserBgmCollectionSyncStatusVo getSyncStatus(Long userId);

    List<CollectionConflictVo> getConflicts(Long userId, Long taskId, int offset, int limit);

    UserBgmCollectionSyncStatusVo resolveConflict(Long userId, Long taskId, long conflictId, long conflictVersion, int selectedType);

    UserBgmCollectionSyncStatusVo resolveConflicts(Long userId, Long taskId,
            List<com.ligg.flowclient.module.dto.CollectionConflictResolveDto.Item> decisions);
}
