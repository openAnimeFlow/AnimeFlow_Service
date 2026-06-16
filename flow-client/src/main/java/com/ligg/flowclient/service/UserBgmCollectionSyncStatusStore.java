package com.ligg.flowclient.service;

import com.ligg.common.constants.Constants;
import com.ligg.common.statuenum.BgmCollectionSyncStatus;
import com.ligg.flowclient.module.vo.UserBgmCollectionSyncStatusVo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class UserBgmCollectionSyncStatusStore {

    static final long STATUS_TTL_SECONDS = 24 * 60 * 60;
    static final long LOCK_TTL_SECONDS = 30 * 60;

    private final RedisTemplate<String, Object> redisTemplate;

    public UserBgmCollectionSyncStatusVo getStatus(Long userId) {
        Object cached = redisTemplate.opsForValue().get(statusKey(userId));
        if (cached instanceof UserBgmCollectionSyncStatusVo vo) {
            return vo;
        }
        UserBgmCollectionSyncStatusVo idle = new UserBgmCollectionSyncStatusVo();
        idle.setStatus(BgmCollectionSyncStatus.IDLE);
        idle.setUserId(userId);
        idle.setSyncedCount(0);
        idle.setTotalCount(0);
        return idle;
    }

    public void saveStatus(Long userId, UserBgmCollectionSyncStatusVo status) {
        redisTemplate.opsForValue().set(statusKey(userId), status, STATUS_TTL_SECONDS, TimeUnit.SECONDS);
    }

    public boolean tryAcquireLock(Long userId) {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                lockKey(userId), "1", LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(locked);
    }

    public void releaseLock(Long userId) {
        redisTemplate.delete(lockKey(userId));
    }

    private static String statusKey(Long userId) {
        return Constants.BGM_COLLECTION_SYNC_STATUS_KEY + ':' + userId;
    }

    private static String lockKey(Long userId) {
        return Constants.BGM_COLLECTION_SYNC_LOCK_KEY + ':' + userId;
    }
}
