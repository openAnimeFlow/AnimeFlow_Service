package com.ligg.flowclient.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.entity.UserBgmCollectionEntity;
import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.thirdparty.bangumi.request.UpdateCollectionBody;
import com.ligg.common.thirdparty.bangumi.response.SubjectDetailDto;
import com.ligg.common.thirdparty.bangumi.response.UserCollectionsDto;
import com.ligg.flowclient.mapper.CollectionSyncConflictMapper;
import com.ligg.flowclient.mapper.CollectionSyncTaskMapper;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.module.entity.CollectionSyncConflictEntity;
import com.ligg.flowclient.module.entity.CollectionSyncTaskEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Callers hold the task lock then the subject lock. HTTP is always outside transactions.
 */
@Service
@RequiredArgsConstructor
public class CollectionSyncItemExecutor {
    private final CollectionSyncTaskService tasks;
    private final CollectionSyncTaskMapper taskMapper;
    private final CollectionSyncConflictMapper items;
    private final UserBgmCollectionMapper collections;
    private final LocalCollectionWriter writer;
    private final CollectionWriteLock locks;
    private final BangumiOAuthExecutor oauthExecutor;
    private final BangumiClient client;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    String scanSnapshot(UserCollectionsDto.Item item) {
        var snapshot = json.convertValue(item, SubjectDetailDto.class);
        var interest = snapshot.getInterest();
        if (interest != null) {
            if (interest.getRate() == null) interest.setRate(0);
            if (interest.getComment() == null) interest.setComment("");
            if (interest.getTags() == null) interest.setTags(List.of());
            if (interest.getEpStatus() == null) interest.setEpStatus(0);
            if (interest.getVolStatus() == null) interest.setVolStatus(0);
            if (interest.getPrivately() == null) interest.setPrivately(false);
        }
        return writer.writeJson(snapshot);
    }

    private SubjectDetailDto scanned(CollectionSyncConflictEntity item) {
        try {
            return json.readValue(item.getScanSnapshot(), SubjectDetailDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("收藏扫描快照无效", e);
        }
    }

    public void resolve(Long userId, Long taskId, long itemId, long version, int selectedType) {
        if (selectedType < 1 || selectedType > 5) throw new IllegalArgumentException("收藏分类无效");
        locks.execute(userId, -1, () -> {
            var task = tasks.owned(userId, taskId);
            tasks.requireBinding(task);
            var item = items.selectById(itemId);
            if (item == null || !Objects.equals(item.getTaskId(), taskId) || !Objects.equals(item.getUserId(), userId))
                throw new IllegalArgumentException("冲突不存在");
            return locks.execute(userId, item.getSubjectId(), () -> {
                // Replaying an accepted identical decision is safe, including a lost response.
                if (Objects.equals(item.getConflictVersion(), version)
                        && Objects.equals(item.getSelectedType(), selectedType)
                        && List.of("RESOLUTION_PENDING", "APPLY_PENDING", "FAILED", "DONE").contains(item.getStatus()))
                    return null;
                if (!"CONFLICT".equals(item.getStatus()) || !Objects.equals(item.getConflictVersion(), version))
                    throw new IllegalArgumentException("STALE_CONFLICT：请刷新后重新选择");
                var local = writer.find(userId, item.getSubjectId());
                var remote = read(task, item);
                if (local == null || !Objects.equals(local.getVersion(), item.getLocalVersion())
                        || !same(fields(remote), writer.readPayload(item.getRemoteSnapshot()))) {
                    conflict(item, local, remote);
                    tasks.summarize(task);
                    throw new IllegalArgumentException("STALE_CONFLICT：双方收藏已变化，请刷新后重新选择");
                }
                tasks.requireBinding(task);
                // Persist authorization before dispatch. Do not change local content until remote confirmation.
                item.setSelectedType(selectedType);
                item.setDesiredPayload(writer.writeJson(desired(local, remote, selectedType)));
                item.setOperation("RESOLVE");
                item.setStatus("RESOLUTION_PENDING");
                item.setErrorCode(null);
                tx.executeWithoutResult(ignored -> {
                    save(item);
                    // Accepted decisions are no longer unresolved conflicts in the UI.
                    tasks.summarize(task);
                    taskMapper.update(null, new LambdaUpdateWrapper<CollectionSyncTaskEntity>()
                            .eq(CollectionSyncTaskEntity::getId, taskId)
                            .ne(CollectionSyncTaskEntity::getStatus, "CANCELLED")
                            .set(CollectionSyncTaskEntity::getStatus, "RUNNING")
                            .set(CollectionSyncTaskEntity::getPhase, "RESOLVING")
                            .setSql("status_version=status_version+1"));
                });
                return null;
            });
        });
    }

    public void execute(CollectionSyncTaskEntity task, CollectionSyncConflictEntity item) {
        locks.execute(task.getUserId(), item.getSubjectId(), () -> {
            if (List.of("DONE", "CONFLICT").contains(item.getStatus())) return null;
            tasks.requireBinding(task);
            var local = writer.find(task.getUserId(), item.getSubjectId());
            boolean prepared = item.getDesiredPayload() != null;
            boolean useScan = !prepared && "PLANNED".equals(item.getStatus()) && item.getScanSnapshot() != null;
            var remote = useScan ? scanned(item) : read(task, item);
            if (useScan && remote.getInterest() == null) {
                if (local == null) {
                    // No collection information to import. Do not invent a category or fetch details.
                    item.setStatus("DONE"); item.setOperation("UNCHANGED");
                    item.setErrorCode(null); item.setResolvedAt(LocalDateTime.now());
                    save(item);
                    return null;
                }
                // A local collection may need an upload; validate the live remote before any write.
                remote = read(task, item);
                useScan = false;
            }
            var current = fields(remote);
            UpdateCollectionBody target;
            if (prepared) {
                target = writer.readPayload(item.getDesiredPayload());
                if (!Objects.equals(local == null ? null : local.getVersion(), item.getLocalVersion())) {
                    conflict(item, local, remote);
                    return null;
                }
                // An uncertain PUT may have succeeded. Confirm before deciding to resend.
                if (!matches(target, current) && !same(current, writer.readPayload(item.getRemoteSnapshot()))) {
                    conflict(item, local, remote);
                    return null;
                }
            } else {
                if (local != null && current.getType() != null && !Objects.equals(local.getType(), current.getType())) {
                    conflict(item, local, remote);
                    return null;
                }
                if (local == null && current.getType() == null)
                    throw new IllegalStateException("远端收藏在扫描后消失，请重试");
                target = desired(local, remote, null);
                if (useScan && !matches(target, current)) {
                    // Only entries that may write remotely need a fresh detail request.
                    var latest = read(task, item);
                    var latestFields = fields(latest);
                    if (!matches(target, latestFields) && !same(current, latestFields)) {
                        conflict(item, local, latest);
                        return null;
                    }
                    remote = latest;
                    current = latestFields;
                }
                item.setLocalVersion(local == null ? null : local.getVersion());
                item.setLocalSnapshot(local == null ? null : writer.writeJson(localFields(local)));
                item.setRemoteSnapshot(writer.writeJson(current));
                item.setDesiredPayload(writer.writeJson(target));
                item.setOperation(local == null ? "IMPORT" : matches(target, current) ? "UNCHANGED" : "UPLOAD");
            }
            item.setStatus("APPLY_PENDING");
            save(item); // Durable intent and baseline must commit before HTTP.
            var oauth = tasks.requireBinding(task);
            if (!matches(target, current)) {
                oauthExecutor.execute(oauth, token -> {
                    client.updateCollection(token, item.getSubjectId(), target);
                    return null;
                });
                remote = read(task, item);
                if (!matches(target, fields(remote))) {
                    conflict(item, local, remote);
                    return null;
                }
            }
            tasks.requireBinding(task);
            finish(task, item, local, remote);
            return null;
        });
    }

    private SubjectDetailDto read(CollectionSyncTaskEntity task, CollectionSyncConflictEntity item) {
        UserOauthEntity oauth = tasks.requireBinding(task);
        var remote = oauthExecutor.execute(oauth, token -> client.getSubject(item.getSubjectId(), token));
        tasks.requireBinding(task);
        if (remote == null || remote.getId() == null || !Objects.equals(remote.getId(), item.getSubjectId())
                || !Objects.equals(remote.getType(), task.getSubjectType()))
            throw new IllegalStateException("Bangumi 条目响应不完整或大类不符");
        if (remote.getInterest() != null && (remote.getInterest().getType() == null
                || remote.getInterest().getType() < 1 || remote.getInterest().getType() > 5))
            throw new IllegalStateException("Bangumi 收藏分类响应无效");
        return remote;
    }

    private void finish(CollectionSyncTaskEntity task, CollectionSyncConflictEntity item,
                        UserBgmCollectionEntity local, SubjectDetailDto remote) {
        var interest = remote.getInterest();
        if (interest == null) throw new IllegalStateException("远端未确认收藏");
        tx.executeWithoutResult(ignored -> {
            var row = local == null ? new UserBgmCollectionEntity() : local;
            long previous = local == null || local.getVersion() == null ? 0 : local.getVersion();
            row.setUserId(task.getUserId());
            row.setSubjectId(item.getSubjectId());
            row.setSubjectType(task.getSubjectType());
            row.setType(interest.getType());
            row.setRate(interest.getRate());
            row.setComment(interest.getComment() == null ? "" : interest.getComment());
            row.setTags(writer.writeJson(interest.getTags() == null ? List.of() : interest.getTags()));
            row.setIsPrivate(Boolean.TRUE.equals(interest.getPrivately()));
            if (local == null) {
                row.setEpStatus(interest.getEpStatus());
                row.setVolStatus(interest.getVolStatus());
                row.setCreateTime(LocalDateTime.now());
            }
            row.setLocalUpdatedAt(LocalDateTime.now());
            row.setVersion(previous + 1);
            row.setBgmInterestId(interest.getId());
            row.setBgmUpdatedAt(interest.getUpdatedAt());
            row.setSyncTime(LocalDateTime.now());
            row.setSyncOauthId(task.getOauthId());
            row.setBgmAccountUid(task.getBgmAccountUid());
            row.setRemoteSyncStatus("SYNCED");
            row.setPendingPayload(null);
            row.setRemoteBaseline(null);
            row.setNextRetryAt(null);
            row.setRetryCount(0);
            if (local == null) collections.insert(row);
            else {
                // Explicit null writes and expected version protect against stale completion.
                int count = collections.update(null, new LambdaUpdateWrapper<UserBgmCollectionEntity>()
                        .eq(UserBgmCollectionEntity::getId, row.getId())
                        .eq(UserBgmCollectionEntity::getVersion, previous)
                        .set(UserBgmCollectionEntity::getType, row.getType())
                        .set(UserBgmCollectionEntity::getRate, row.getRate())
                        .set(UserBgmCollectionEntity::getComment, row.getComment())
                        .set(UserBgmCollectionEntity::getTags, row.getTags())
                        .set(UserBgmCollectionEntity::getIsPrivate, row.getIsPrivate())
                        .set(UserBgmCollectionEntity::getVersion, row.getVersion())
                        .set(UserBgmCollectionEntity::getLocalUpdatedAt, row.getLocalUpdatedAt())
                        .set(UserBgmCollectionEntity::getBgmInterestId, row.getBgmInterestId())
                        .set(UserBgmCollectionEntity::getBgmUpdatedAt, row.getBgmUpdatedAt())
                        .set(UserBgmCollectionEntity::getSyncTime, row.getSyncTime())
                        .set(UserBgmCollectionEntity::getSyncOauthId, row.getSyncOauthId())
                        .set(UserBgmCollectionEntity::getBgmAccountUid, row.getBgmAccountUid())
                        .set(UserBgmCollectionEntity::getRemoteSyncStatus, "SYNCED")
                        .set(UserBgmCollectionEntity::getPendingPayload, null)
                        .set(UserBgmCollectionEntity::getRemoteBaseline, null)
                        .set(UserBgmCollectionEntity::getNextRetryAt, null)
                        .set(UserBgmCollectionEntity::getRetryCount, 0));
                if (count != 1) throw new IllegalStateException("STALE_CONFLICT");
            }
            item.setStatus("DONE");
            item.setResolvedAt(LocalDateTime.now());
            item.setErrorCode(null);
            save(item);
        });
    }

    private void conflict(CollectionSyncConflictEntity item, UserBgmCollectionEntity local, SubjectDetailDto remote) {
        tx.executeWithoutResult(ignored -> {
            item.setLocalType(local == null ? null : local.getType());
            item.setRemoteType(fields(remote).getType());
            item.setLocalVersion(local == null ? null : local.getVersion());
            item.setLocalSnapshot(local == null ? null : writer.writeJson(localFields(local)));
            item.setRemoteSnapshot(writer.writeJson(fields(remote)));
            item.setConflictVersion((item.getConflictVersion() == null ? 0 : item.getConflictVersion()) + 1);
            item.setStatus("CONFLICT");
            item.setSelectedType(null);
            item.setDesiredPayload(null);
            item.setErrorCode("STALE_CONFLICT");
            item.setResolvedAt(null);
            save(item);
            if (local != null) {
                collections.update(null, new LambdaUpdateWrapper<UserBgmCollectionEntity>()
                        .eq(UserBgmCollectionEntity::getId, local.getId())
                        .eq(UserBgmCollectionEntity::getVersion, local.getVersion())
                        .set(UserBgmCollectionEntity::getRemoteSyncStatus, "CONFLICT"));
            }
        });
    }

    private void save(CollectionSyncConflictEntity item) {
        // updateById omits null values by default; explicitly clear obsolete decisions and baselines.
        items.update(null, new LambdaUpdateWrapper<CollectionSyncConflictEntity>()
                .eq(CollectionSyncConflictEntity::getId, item.getId())
                .set(CollectionSyncConflictEntity::getStatus, item.getStatus())
                .set(CollectionSyncConflictEntity::getLocalType, item.getLocalType())
                .set(CollectionSyncConflictEntity::getRemoteType, item.getRemoteType())
                .set(CollectionSyncConflictEntity::getLocalVersion, item.getLocalVersion())
                .set(CollectionSyncConflictEntity::getConflictVersion, item.getConflictVersion())
                .set(CollectionSyncConflictEntity::getSelectedType, item.getSelectedType())
                .set(CollectionSyncConflictEntity::getLocalSnapshot, item.getLocalSnapshot())
                .set(CollectionSyncConflictEntity::getRemoteSnapshot, item.getRemoteSnapshot())
                .set(CollectionSyncConflictEntity::getDesiredPayload, item.getDesiredPayload())
                .set(CollectionSyncConflictEntity::getOperation, item.getOperation())
                .set(CollectionSyncConflictEntity::getErrorCode, item.getErrorCode())
                .set(CollectionSyncConflictEntity::getResolvedAt, item.getResolvedAt()));
    }

    UpdateCollectionBody desired(UserBgmCollectionEntity local, SubjectDetailDto remote, Integer selected) {
        var result = local == null ? new UpdateCollectionBody()
                : remote.getInterest() == null ? localFields(local) : writer.readPayload(local.getPendingPayload());
        // Keep all non-type dirty fields, including explicit empty values.
        if (selected != null) result.setType(selected);
        else if (local != null) result.setType(local.getType());
        return result;
    }

    UpdateCollectionBody fields(SubjectDetailDto remote) {
        var result = new UpdateCollectionBody();
        if (remote.getInterest() != null) {
            var i = remote.getInterest();
            result.setType(i.getType());
            result.setRate(i.getRate() == null ? 0 : i.getRate());
            result.setComment(i.getComment() == null ? "" : i.getComment());
            result.setTags(i.getTags() == null ? List.of() : i.getTags());
            result.setPrivate_(Boolean.TRUE.equals(i.getPrivately()));
        }
        return result;
    }

    private UpdateCollectionBody localFields(UserBgmCollectionEntity row) {
        var result = new UpdateCollectionBody();
        result.setType(row.getType());
        result.setRate(row.getRate());
        result.setComment(row.getComment());
        result.setPrivate_(row.getIsPrivate());
        try {
            result.setTags(row.getTags() == null ? List.of() : json.readValue(row.getTags(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                    }));
        } catch (Exception e) {
            throw new IllegalStateException("本地标签无效", e);
        }
        return result;
    }

    boolean matches(UpdateCollectionBody target, UpdateCollectionBody current) {
        var expected = json.valueToTree(target);
        var actual = json.valueToTree(current);
        var fields = expected.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (!CollectionFieldComparison.same(field.getKey(), field.getValue(), actual.get(field.getKey())))
                return false;
        }
        return true;
    }

    private boolean same(UpdateCollectionBody a, UpdateCollectionBody b) {
        return matches(a, b) && matches(b, a);
    }
}
