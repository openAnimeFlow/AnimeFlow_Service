package com.ligg.flowclient.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.statuenum.BgmCollectionSyncStatus;
import com.ligg.flowclient.mapper.CollectionSyncConflictMapper;
import com.ligg.flowclient.mapper.CollectionSyncTaskMapper;
import com.ligg.flowclient.module.entity.CollectionSyncConflictEntity;
import com.ligg.flowclient.module.entity.CollectionSyncTaskEntity;
import com.ligg.flowclient.module.vo.CollectionConflictVo;
import com.ligg.flowclient.module.vo.UserBgmCollectionSyncStatusVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CollectionSyncTaskService {
    private final CollectionSyncTaskMapper tasks;
    private final CollectionSyncConflictMapper items;
    private final CollectionWriteLock locks;
    private final BangumiOAuthTokenService tokens;

    // Reserved negative subject IDs serialize task registration; no HTTP under this lock.
    public CollectionSyncTaskEntity createOrGet(Long userId, int subjectType, String requestId) {
        if (!Set.of(1,2,3,4,6).contains(subjectType)) throw new IllegalArgumentException("条目大类无效");
        if (requestId == null || requestId.isBlank() || requestId.length() > 80)
            throw new IllegalArgumentException("requestId 无效");
        return locks.execute(userId, -2, () -> {
            UserOauthEntity oauth = tokens.requireBangumiOauth(userId);
            var previous = tasks.selectRequest(userId, requestId);
            if (previous != null) return previous;
            var active = tasks.selectActive(userId);
            if (active != null && sameBinding(active, oauth)) return active;
            if (active != null) cancel(active);
            var task = new CollectionSyncTaskEntity();
            task.setUserId(userId); task.setSubjectType(subjectType);
            task.setRequestId(requestId); task.setOauthId(oauth.getId());
            task.setBgmAccountUid(oauth.getPlatformUid());
            task.setStatus("QUEUED"); task.setPhase("SCANNING");
            task.setCreatedAt(LocalDateTime.now()); task.setStatusVersion(0L);
            task.setTotalCount(0); task.setImportedCount(0); task.setUploadedCount(0);
            task.setUnchangedCount(0); task.setConflictCount(0); task.setResolvedCount(0); task.setFailedCount(0);
            tasks.insert(task); // Autocommit completes before any async dispatch.
            return task;
        });
    }

    public boolean sameBinding(CollectionSyncTaskEntity task, UserOauthEntity oauth) {
        return oauth != null && Objects.equals(task.getOauthId(), oauth.getId())
                && Objects.equals(task.getBgmAccountUid(), oauth.getPlatformUid());
    }

    public UserOauthEntity requireBinding(CollectionSyncTaskEntity task) {
        var oauth = tokens.findBangumiOauth(task.getUserId());
        if (!sameBinding(task, oauth)) {
            cancel(task);
            throw new IllegalArgumentException("SYNC_BINDING_CHANGED");
        }
        return oauth;
    }

    public void cancel(CollectionSyncTaskEntity task) {
        tasks.update(null, new LambdaUpdateWrapper<CollectionSyncTaskEntity>()
                .eq(CollectionSyncTaskEntity::getId,  task.getId())
                .set(CollectionSyncTaskEntity::getStatus,  "CANCELLED")
                .set(CollectionSyncTaskEntity::getErrorCode,  "SYNC_BINDING_CHANGED")
                .set(CollectionSyncTaskEntity::getFinishedAt,  LocalDateTime.now())
                .setSql("status_version=status_version+1"));
        task.setStatus("CANCELLED");
    }

    public CollectionSyncTaskEntity owned(Long userId, Long taskId) {
        var task = tasks.selectOwned(userId, taskId);
        if (task == null) throw new IllegalArgumentException("同步任务不存在");
        return task;
    }

    public List<CollectionConflictVo> conflicts(Long userId, Long taskId, int offset, int limit) {
        var task = owned(userId, taskId);
        requireBinding(task);
        return items.selectConflicts(userId, taskId, Math.min(Math.max(limit,1),100), Math.max(offset,0))
                .stream().map(item -> {
                    var vo = new CollectionConflictVo();
                    vo.setConflictId(item.getId()); vo.setSubjectId(item.getSubjectId());
                    vo.setSubjectType(item.getSubjectType()); vo.setSubjectName(item.getSubjectName());
                    vo.setSubjectImage(item.getSubjectImage()); vo.setLocalType(item.getLocalType());
                    vo.setRemoteType(item.getRemoteType()); vo.setLocalVersion(item.getLocalVersion());
                    vo.setConflictVersion(item.getConflictVersion()); vo.setStatus(item.getStatus());
                    return vo;
                }).toList();
    }

    public List<CollectionSyncConflictEntity> allItems(Long taskId) {
        return items.selectList(new LambdaQueryWrapper<CollectionSyncConflictEntity>()
                .eq(CollectionSyncConflictEntity::getTaskId, taskId)
                .orderByAsc(CollectionSyncConflictEntity::getId));
    }

    public void summarize(CollectionSyncTaskEntity task) {
        requireBinding(task);
        var rows = allItems(task.getId());
        int conflicts = 0, failed = 0, pending = 0, imported = 0, uploaded = 0, unchanged = 0, resolved = 0;
        for (var row : rows) {
            if ("CONFLICT".equals(row.getStatus())) conflicts++;
            else if ("FAILED".equals(row.getStatus())) failed++;
            else if (!"DONE".equals(row.getStatus())) pending++;
            else {
                switch (row.getOperation()) {
                    case "IMPORT" -> imported++;
                    case "UPLOAD" -> uploaded++;
                    case "RESOLVE" -> resolved++;
                    default -> unchanged++;
                }
            }
        }
        String status = pending > 0 ? "RUNNING" : conflicts > 0 ? "WAITING_CONFLICT"
                : failed > 0 ? "PARTIAL_FAILED" : "SUCCESS";
        tasks.update(null, new LambdaUpdateWrapper<CollectionSyncTaskEntity>()
                .eq(CollectionSyncTaskEntity::getId, task.getId())
                .ne(CollectionSyncTaskEntity::getStatus, "CANCELLED")
                .set(CollectionSyncTaskEntity::getStatus, status)
                .set(CollectionSyncTaskEntity::getTotalCount, rows.size())
                .set(CollectionSyncTaskEntity::getImportedCount, imported)
                .set(CollectionSyncTaskEntity::getUploadedCount, uploaded)
                .set(CollectionSyncTaskEntity::getUnchangedCount, unchanged)
                .set(CollectionSyncTaskEntity::getConflictCount, conflicts)
                .set(CollectionSyncTaskEntity::getResolvedCount, resolved)
                .set(CollectionSyncTaskEntity::getFailedCount, failed)
                .set(CollectionSyncTaskEntity::getHeartbeatAt, LocalDateTime.now())
                .set(CollectionSyncTaskEntity::getFinishedAt, "SUCCESS".equals(status) ? LocalDateTime.now() : null)
                .setSql("status_version=status_version+1"));
    }

    public UserBgmCollectionSyncStatusVo status(Long userId) {
        var task = tasks.selectActive(userId);
        if (task != null && !sameBinding(task,tokens.findBangumiOauth(userId))) cancel(task);
        if (task == null) task = tasks.selectLatest(userId);
        if (task != null) return toStatus(task);
        var vo = new UserBgmCollectionSyncStatusVo();
        vo.setUserId(userId); vo.setStatus(BgmCollectionSyncStatus.IDLE);
        vo.setSyncedCount(0); vo.setTotalCount(0);
        return vo;
    }

    public UserBgmCollectionSyncStatusVo toStatus(CollectionSyncTaskEntity task) {
        var vo = new UserBgmCollectionSyncStatusVo();
        vo.setTaskId(task.getId()); vo.setUserId(task.getUserId());
        vo.setStatus(BgmCollectionSyncStatus.valueOf(task.getStatus())); vo.setPhase(task.getPhase());
        vo.setTotalCount(task.getTotalCount()); vo.setImportedCount(task.getImportedCount());
        vo.setUploadedCount(task.getUploadedCount()); vo.setUnchangedCount(task.getUnchangedCount());
        vo.setPendingConflictCount(task.getConflictCount()); vo.setFailedCount(task.getFailedCount());
        vo.setStatusVersion(task.getStatusVersion());
        vo.setSyncedCount(zero(task.getImportedCount())+zero(task.getUploadedCount())
                +zero(task.getUnchangedCount())+zero(task.getResolvedCount()));
        vo.setMessage(task.getErrorCode());
        if (task.getStartedAt()!=null) vo.setStartedAt(task.getStartedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        if (task.getFinishedAt()!=null) vo.setFinishedAt(task.getFinishedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        return vo;
    }
    private int zero(Integer n) { return n == null ? 0 : n; }
}
