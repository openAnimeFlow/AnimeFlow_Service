package com.ligg.flowclient.service.impl;

import com.ligg.common.statuenum.BgmCollectionSyncStatus;
import com.ligg.flowclient.module.vo.UserBgmCollectionSyncStatusVo;
import com.ligg.flowclient.service.BangumiOAuthTokenService;
import com.ligg.flowclient.service.UserBgmCollectionSyncRunner;
import com.ligg.flowclient.service.UserBgmCollectionSyncService;
import com.ligg.flowclient.service.UserBgmCollectionSyncStatusStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserBgmCollectionSyncServiceImpl implements UserBgmCollectionSyncService {

    private static final long COOLDOWN_SECONDS = 10 * 60;

    private final BangumiOAuthTokenService bangumiOAuthTokenService;
    private final UserBgmCollectionSyncStatusStore statusStore;
    private final UserBgmCollectionSyncRunner syncRunner;

    @Override
    public UserBgmCollectionSyncStatusVo triggerSync(Long userId, int subjectType) {
        bangumiOAuthTokenService.requireBangumiOauth(userId);

        UserBgmCollectionSyncStatusVo current = statusStore.getStatus(userId);
        if (current.getStatus() == BgmCollectionSyncStatus.RUNNING) {
            return current;
        }
        if (current.getStatus() == BgmCollectionSyncStatus.SUCCESS
                && current.getFinishedAt() != null
                && System.currentTimeMillis() - current.getFinishedAt() < COOLDOWN_SECONDS * 1000) {
            current.setMessage("同步过于频繁，请稍后再试");
            return current;
        }

        if (!statusStore.tryAcquireLock(userId)) {
            UserBgmCollectionSyncStatusVo running = statusStore.getStatus(userId);
            if (running.getStatus() != BgmCollectionSyncStatus.RUNNING) {
                running.setStatus(BgmCollectionSyncStatus.RUNNING);
                running.setMessage("同步任务进行中");
            }
            return running;
        }

        UserBgmCollectionSyncStatusVo status = new UserBgmCollectionSyncStatusVo();
        status.setStatus(BgmCollectionSyncStatus.RUNNING);
        status.setUserId(userId);
        status.setSyncedCount(0);
        status.setTotalCount(0);
        status.setStartedAt(System.currentTimeMillis());
        status.setMessage("同步已开始");
        statusStore.saveStatus(userId, status);

        syncRunner.runSync(userId, subjectType);
        return status;
    }

    @Override
    public UserBgmCollectionSyncStatusVo getSyncStatus(Long userId) {
        return statusStore.getStatus(userId);
    }
}
