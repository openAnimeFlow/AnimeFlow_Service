package com.ligg.flowclient.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.entity.UserBgmCollectionEntity;
import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.exception.LoginExpiredException;
import com.ligg.common.statuenum.BgmCollectionSyncStatus;
import com.ligg.common.thirdparty.bangumi.response.UserCollectionsDto;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.module.vo.UserBgmCollectionSyncStatusVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserBgmCollectionSyncRunner {

    private static final int PAGE_SIZE = 50;
    private static final int[] COLLECTION_TYPES = {1, 2, 3, 4, 5};

    private final BangumiOAuthTokenService bangumiOAuthTokenService;
    private final BangumiOAuthExecutor bangumiOAuthExecutor;
    private final BangumiClient bangumiClient;
    private final UserBgmCollectionMapper userBgmCollectionMapper;
    private final UserBgmCollectionSyncStatusStore statusStore;
    private final ObjectMapper objectMapper;

    @Async("bgmCollectionSyncExecutor")
    public void runSync(Long userId, int subjectType) {
        try {
            executeSync(userId, subjectType);
        } catch (LoginExpiredException e) {
            log.warn("Bangumi 收藏同步登录过期 userId={}", userId);
            markFailed(userId, "Bangumi 授权已过期，请重新绑定");
        } catch (Exception e) {
            log.error("Bangumi 收藏同步异常 userId={}", userId, e);
            markFailed(userId, e.getMessage() != null ? e.getMessage() : "同步失败");
        } finally {
            statusStore.releaseLock(userId);
        }
    }

    private void executeSync(Long userId, int subjectType) {
        UserOauthEntity oauth = bangumiOAuthTokenService.requireBangumiOauth(userId);
        LocalDateTime syncStartTime = LocalDateTime.now();
        int syncedCount = 0;
        int totalCount = 0;

        updateProgress(userId, syncedCount, totalCount, "正在同步 Bangumi 收藏…");

        for (int collectionType : COLLECTION_TYPES) {
            int offset = 0;
            while (true) {
                final int type = collectionType;
                final int pageOffset = offset;
                UserCollectionsDto page = bangumiOAuthExecutor.execute(oauth, accessToken ->
                        bangumiClient.getMeCollections(
                                accessToken, subjectType, type, PAGE_SIZE, pageOffset));
                List<UserCollectionsDto.Item> items = page.getData();
                if (items == null || items.isEmpty()) {
                    break;
                }
                for (UserCollectionsDto.Item item : items) {
                    if (item == null || item.getInterest() == null || item.getInterest().getId() == null) {
                        continue;
                    }
                    upsertCollection(userId, item);
                    syncedCount++;
                }
                if (page.getTotal() != null) {
                    totalCount = Math.max(totalCount, page.getTotal());
                }
                updateProgress(userId, syncedCount, totalCount, "已同步 " + syncedCount + " 条收藏");

                if (items.size() < PAGE_SIZE) {
                    break;
                }
                offset += PAGE_SIZE;
                if (page.getTotal() != null && offset >= page.getTotal()) {
                    break;
                }
            }
        }

        userBgmCollectionMapper.delete(new LambdaQueryWrapper<UserBgmCollectionEntity>()
                .eq(UserBgmCollectionEntity::getUserId, userId)
                .lt(UserBgmCollectionEntity::getSyncTime, syncStartTime));

        UserBgmCollectionSyncStatusVo status = statusStore.getStatus(userId);
        status.setStatus(BgmCollectionSyncStatus.SUCCESS);
        status.setUserId(userId);
        status.setSyncedCount(syncedCount);
        status.setTotalCount(totalCount);
        status.setFinishedAt(System.currentTimeMillis());
        status.setMessage("同步完成，共同步 " + syncedCount + " 条收藏");
        statusStore.saveStatus(userId, status);
        log.info("Bangumi 收藏同步完成 userId={} syncedCount={}", userId, syncedCount);
    }

    private void markFailed(Long userId, String message) {
        UserBgmCollectionSyncStatusVo status = statusStore.getStatus(userId);
        status.setStatus(BgmCollectionSyncStatus.FAILED);
        status.setUserId(userId);
        status.setFinishedAt(System.currentTimeMillis());
        status.setMessage(message);
        statusStore.saveStatus(userId, status);
    }

    private void updateProgress(Long userId, int syncedCount, int totalCount, String message) {
        UserBgmCollectionSyncStatusVo status = statusStore.getStatus(userId);
        status.setStatus(BgmCollectionSyncStatus.RUNNING);
        status.setUserId(userId);
        status.setSyncedCount(syncedCount);
        status.setTotalCount(totalCount);
        status.setMessage(message);
        if (status.getStartedAt() == null) {
            status.setStartedAt(System.currentTimeMillis());
        }
        statusStore.saveStatus(userId, status);
    }

    private void upsertCollection(Long userId, UserCollectionsDto.Item item) {
        UserCollectionsDto.Interest interest = item.getInterest();
        UserBgmCollectionEntity existing = userBgmCollectionMapper.selectOne(
                new LambdaQueryWrapper<UserBgmCollectionEntity>()
                        .eq(UserBgmCollectionEntity::getBgmInterestId, interest.getId()));

        UserBgmCollectionEntity row = existing != null ? existing : new UserBgmCollectionEntity();
        row.setUserId(userId);
        row.setSubjectId(item.getId());
        row.setImages(toJson(item.getImages()));
        row.setBgmInterestId(interest.getId());
        row.setRate(interest.getRate() != null ? interest.getRate() : 0);
        row.setType(interest.getType());
        row.setComment(StringUtils.hasText(interest.getComment()) ? interest.getComment() : "");
        row.setTags(toJson(interest.getTags()));
        row.setEpStatus(interest.getEpStatus() != null ? interest.getEpStatus() : 0);
        row.setVolStatus(interest.getVolStatus() != null ? interest.getVolStatus() : 0);
        row.setIsPrivate(Boolean.TRUE.equals(interest.getPrivate_()));
        row.setBgmUpdatedAt(interest.getUpdatedAt() != null ? interest.getUpdatedAt() : 0L);
        row.setSyncTime(LocalDateTime.now());

        if (existing == null) {
            row.setCreateTime(LocalDateTime.now());
            userBgmCollectionMapper.insert(row);
        } else {
            userBgmCollectionMapper.updateById(row);
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 JSON 失败", e);
        }
    }
}
