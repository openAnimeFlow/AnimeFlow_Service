package com.ligg.flowclient.service.impl;

import com.ligg.flowclient.module.vo.*;
import com.ligg.flowclient.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserBgmCollectionSyncServiceImpl implements UserBgmCollectionSyncService {
    private final CollectionSyncTaskService tasks;
    private final UserBgmCollectionSyncRunner runner;
    private final CollectionSyncItemExecutor executor;

    @Override
    public UserBgmCollectionSyncStatusVo triggerSync(Long userId, int subjectType, String requestId) {
        var task = tasks.createOrGet(userId, subjectType,
                requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId);
        if (tasks.isWithinCooldown(task)) {
            var status = tasks.toStatus(task);
            status.setMessage("同步过于频繁，请稍后再试");
            return status;
        }
        runner.runTask(task.getId());
        return tasks.toStatus(task);
    }
    @Override
    public UserBgmCollectionSyncStatusVo getSyncStatus(Long userId) { return tasks.status(userId); }
    @Override
    public List<CollectionConflictVo> getConflicts(Long userId, Long taskId, int offset, int limit) {
        return tasks.conflicts(userId, taskId, offset, limit);
    }
    @Override
    public UserBgmCollectionSyncStatusVo resolveConflict(Long userId, Long taskId, long conflictId,
                                                       long conflictVersion, int selectedType) {
        executor.resolve(userId, taskId, conflictId, conflictVersion, selectedType);
        // Batch entry point dispatches once after all decisions have released their locks.
        return tasks.toStatus(tasks.owned(userId,taskId));
    }

    @Override
    public UserBgmCollectionSyncStatusVo resolveConflicts(Long userId, Long taskId,
            List<com.ligg.flowclient.module.dto.CollectionConflictResolveDto.Item> decisions) {
        boolean accepted = false;
        try {
            for (var decision : decisions) {
                executor.resolve(userId, taskId, decision.getConflictId(),
                        decision.getConflictVersion(), decision.getSelectedType());
                accepted = true;
            }
            return tasks.toStatus(tasks.owned(userId, taskId));
        } finally {
            // Also dispatch accepted entries when a later decision is stale.
            // No task/subject lock or transaction is held at this point.
            if (accepted) {
                try {
                    runner.runTask(taskId);
                } catch (java.util.concurrent.RejectedExecutionException rejected) {
                    // Persisted decisions remain recoverable by the scheduler.
                    log.warn("收藏冲突决议已保存，异步队列繁忙，等待定时恢复 taskId={}", taskId);
                }
            }
        }
    }
}
