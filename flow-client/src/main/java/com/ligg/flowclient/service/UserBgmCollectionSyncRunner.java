package com.ligg.flowclient.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.entity.UserBgmCollectionEntity;
import com.ligg.flowclient.mapper.CollectionSyncTaskMapper;
import com.ligg.flowclient.mapper.CollectionSyncConflictMapper;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.module.entity.CollectionSyncTaskEntity;
import com.ligg.flowclient.module.entity.CollectionSyncConflictEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserBgmCollectionSyncRunner {
    private final CollectionSyncTaskMapper taskMapper;
    private final CollectionSyncConflictMapper items;
    private final UserBgmCollectionMapper collections;
    private final CollectionSyncTaskService tasks;
    private final CollectionSyncItemExecutor executor;
    private final CollectionWriteLock locks;
    private final BangumiOAuthExecutor oauth;
    private final BangumiClient client;

    @Async("bgmCollectionSyncExecutor")
    public void runTask(Long taskId) {
        run(taskId);
    }

    @Scheduled(initialDelayString = "${anime-flow.collection-sync.initial-delay-ms:60000}",
            fixedDelayString = "${anime-flow.collection-sync.retry-delay-ms:60000}")
    public void recover() {
        for (var task : taskMapper.selectRecoverable()) run(task.getId());
    }

    private void run(Long taskId) {
        var candidate = taskMapper.selectById(taskId);
        if (candidate == null) return;
        try {
            // Connection-owned lock: process death releases it, and another instance can recover immediately.
            locks.execute(candidate.getUserId(), -1, () -> {
                var task = taskMapper.selectById(taskId);
                if (task == null || List.of("SUCCESS", "CANCELLED").contains(task.getStatus())) return null;
                try {
                    tasks.requireBinding(task);
                    if ("SCANNING".equals(task.getPhase())) scan(task);
                    for (var item : tasks.allItems(taskId)) {
                        if (List.of("CONFLICT", "DONE").contains(item.getStatus())) continue;
                        tasks.requireBinding(task);
                        try {
                            executor.execute(task, item);
                        } catch (Exception e) {
                            tasks.requireBinding(task);

                            items.update(null, new LambdaUpdateWrapper<CollectionSyncConflictEntity>()
                                    .eq(CollectionSyncConflictEntity::getId, item.getId())
                                    .ne(CollectionSyncConflictEntity::getStatus, "DONE")
                                    .ne(CollectionSyncConflictEntity::getStatus, "CONFLICT")
                                    .set(CollectionSyncConflictEntity::getStatus, "FAILED")
                                    .set(CollectionSyncConflictEntity::getErrorCode, "ITEM_RETRY_REQUIRED"));
                            log.warn("收藏同步明细待重试 taskId={} itemId={} error={}", taskId, item.getId(), e.getClass().getSimpleName());
                        }
                        heartbeat(task);
                    }
                    tasks.summarize(task);
                } catch (Exception e) {
                    taskMapper.update(null, new LambdaUpdateWrapper<CollectionSyncTaskEntity>()
                            .eq(CollectionSyncTaskEntity::getId, taskId)
                            .ne(CollectionSyncTaskEntity::getStatus, "CANCELLED")
                            .set(CollectionSyncTaskEntity::getStatus, "PARTIAL_FAILED")
                            .set(CollectionSyncTaskEntity::getErrorCode, "SYNC_RETRY_REQUIRED")
                            .set(CollectionSyncTaskEntity::getHeartbeatAt, LocalDateTime.now())
                            .setSql("status_version=status_version+1"));
                    log.warn("收藏同步任务待恢复 taskId={} error={}", taskId, e.getClass().getSimpleName());
                }
                return null;
            });
        } catch (IllegalStateException busy) {
            // Another instance owns this task. Its connection lock must not be released by us.
            log.debug("收藏任务由其他执行器持有 taskId={}", taskId);
        }
    }

    private void scan(CollectionSyncTaskEntity task) {
        taskMapper.update(null, new LambdaUpdateWrapper<CollectionSyncTaskEntity>()
                .eq(CollectionSyncTaskEntity::getId, task.getId())
                .ne(CollectionSyncTaskEntity::getStatus, "CANCELLED")
                .set(CollectionSyncTaskEntity::getStatus, "RUNNING")
                .set(CollectionSyncTaskEntity::getStartedAt, LocalDateTime.now()));
        // No formal collection changes until all five categories have been read successfully.
        for (int type = 1; type <= 5; type++) {
            int offset = 0;
            var seen = new HashSet<Integer>();
            while (true) {
                var binding = tasks.requireBinding(task);
                int pageOffset = offset, collectionType = type;
                var page = oauth.execute(binding, token -> client.getMeCollections(token, task.getSubjectType(), collectionType, 50, pageOffset));
                tasks.requireBinding(task);
                if (page == null || page.getData() == null) throw new IllegalStateException("收藏分页响应无效");
                if (page.getData().isEmpty()) {
                    if (page.getTotal() != null && offset < page.getTotal())
                        throw new IllegalStateException("收藏分页不完整");
                    break;
                }
                int added = 0;
                for (var remote : page.getData()) {
                    if (remote == null || remote.getId() == null || !task.getSubjectType().equals(remote.getType())
                            || (remote.getInterest() != null && (remote.getInterest().getType() == null
                            || remote.getInterest().getType() < 1 || remote.getInterest().getType() > 5)))
                        throw new IllegalStateException("收藏分页条目无效");
                    if (seen.add(remote.getId())) added++;
                    stage(task, remote.getId(), remote.getNameCN() == null ? remote.getName() : remote.getNameCN(),
                            remote.getImages() == null ? null : remote.getImages().getLarge(), executor.scanSnapshot(remote));
                }
                if (added == 0) throw new IllegalStateException("收藏分页未前进");
                offset += page.getData().size();
                heartbeat(task);
                if (page.getTotal() != null && offset >= page.getTotal()) break;
                if (page.getTotal() == null && page.getData().size() < 50) break;
            }
        }
        // Full union: local-only collections are planned, never deleted.
        for (var row : collections.selectList(new LambdaQueryWrapper<UserBgmCollectionEntity>()
                .eq(UserBgmCollectionEntity::getUserId, task.getUserId())
                .eq(UserBgmCollectionEntity::getSubjectType, task.getSubjectType()))) {
            stage(task, row.getSubjectId(), null, null, null);
        }
        tasks.requireBinding(task);
        taskMapper.update(null, new LambdaUpdateWrapper<CollectionSyncTaskEntity>()
                .eq(CollectionSyncTaskEntity::getId, task.getId())
                .ne(CollectionSyncTaskEntity::getStatus, "CANCELLED")
                .set(CollectionSyncTaskEntity::getPhase, "APPLYING")
                .set(CollectionSyncTaskEntity::getErrorCode, null));
        task.setPhase("APPLYING");
    }

    private void stage(CollectionSyncTaskEntity task, int subjectId, String name, String image, String snapshot) {
        var existing = items.selectOne(new LambdaQueryWrapper<CollectionSyncConflictEntity>()
                .eq(CollectionSyncConflictEntity::getTaskId, task.getId())
                .eq(CollectionSyncConflictEntity::getSubjectId, subjectId));
        if (existing != null) {
            // A restarted scan must replace an earlier partial scan's snapshot.
            if (snapshot != null) {
                items.update(null, new LambdaUpdateWrapper<CollectionSyncConflictEntity>()
                        .eq(CollectionSyncConflictEntity::getId, existing.getId())
                        .eq(CollectionSyncConflictEntity::getStatus, "PLANNED")
                        .set(CollectionSyncConflictEntity::getScanSnapshot, snapshot));
            }
            return;
        }
        var item = new CollectionSyncConflictEntity();
        item.setTaskId(task.getId());
        item.setUserId(task.getUserId());
        item.setSubjectType(task.getSubjectType());
        item.setSubjectId(subjectId);
        item.setSubjectName(name);
        item.setSubjectImage(image);
        item.setScanSnapshot(snapshot);
        item.setStatus("PLANNED");
        item.setConflictVersion(0L);
        items.insert(item);
    }

    private void heartbeat(CollectionSyncTaskEntity task) {
        taskMapper.update(null, new LambdaUpdateWrapper<CollectionSyncTaskEntity>()
                .eq(CollectionSyncTaskEntity::getId, task.getId())
                .ne(CollectionSyncTaskEntity::getStatus, "CANCELLED")
                .set(CollectionSyncTaskEntity::getHeartbeatAt, LocalDateTime.now()));
    }
}
