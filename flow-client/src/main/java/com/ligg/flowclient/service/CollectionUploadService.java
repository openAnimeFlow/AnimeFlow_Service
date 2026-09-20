package com.ligg.flowclient.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.entity.UserBgmCollectionEntity;
import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.exception.LoginExpiredException;
import com.ligg.common.statuenum.CollectionRemoteSyncStatus;
import com.ligg.common.thirdparty.bangumi.request.UpdateCollectionBody;
import com.ligg.common.thirdparty.bangumi.response.SubjectDetailDto;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.module.vo.CollectionUpdateVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

/** 收藏记录在第一阶段充当持久化的合并式发件箱。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionUploadService {
    private final LocalCollectionWriter writer;
    private final UserBgmCollectionMapper mapper;
    private final CollectionWriteLock lock;
    private final BangumiOAuthTokenService tokens;
    private final BangumiOAuthExecutor oauthExecutor;
    private final BangumiClient client;
    private final ObjectMapper json;

    /** 必须在与本地编辑相同的条目级锁下执行。 */
    public CollectionUpdateVo upload(UserBgmCollectionEntity row) {
        return upload(row, false);
    }

    private CollectionUpdateVo upload(UserBgmCollectionEntity row, boolean recovered) {
        if (CollectionRemoteSyncStatus.PENDING != row.getRemoteSyncStatus()) return result(row);
        // 完整同步任务负责数据对账；普通保存操作在任务完成前只保留本地变更。
        if (mapper.countBlockedSyncItems(row.getUserId(), row.getSubjectId()) > 0)
            return mark(row, CollectionRemoteSyncStatus.CONFLICT);
        if (mapper.countActiveSyncTasks(row.getUserId(), row.getSubjectType()) > 0) return result(row);
        String savedPayload = row.getPendingPayload();
        String savedBaseline = row.getRemoteBaseline();
        try {
            var oauth = tokens.findBangumiOauth(row.getUserId());
            if (!sameBinding(row, oauth)) return mark(row, CollectionRemoteSyncStatus.AUTH_REQUIRED);
            var body = writer.readPayload(row.getPendingPayload());
            var detail = oauthExecutor.execute(oauth, token -> client.getSubject(row.getSubjectId(), token));
            var current = remoteFields(detail);
            if (detail.getInterest() == null && body.getType() == null) body.setType(row.getType());
            // 如果在记录远端基线前崩溃，就无法证明当前远端值是旧数据。
            if (recovered && row.getRemoteBaseline() == null && detail.getInterest() != null
                    && !matches(body, current)) return mark(row, CollectionRemoteSyncStatus.CONFLICT);
            if (row.getRemoteBaseline() != null && !matches(body, current)
                    && !sameEditedFields(body, writer.readPayload(row.getRemoteBaseline()), current)) {
                return mark(row, CollectionRemoteSyncStatus.CONFLICT);
            }
            row.setPendingPayload(writer.writeJson(body));
            if (row.getRemoteBaseline() == null) {
                row.setRemoteBaseline(writer.writeJson(current));
            // 在发起 HTTP 请求前提交远端基线，重试时才能检测期间发生的远端编辑。
            persist(row);
            }
            savedBaseline = row.getRemoteBaseline();
            if (!sameBinding(row, tokens.findBangumiOauth(row.getUserId())))
                return mark(row, CollectionRemoteSyncStatus.AUTH_REQUIRED);
            if (!matches(body, current)) {
                oauthExecutor.execute(oauth, token -> {
                    client.updateCollection(token, row.getSubjectId(), body);
                    return null;
                });
                detail = oauthExecutor.execute(oauth, token -> client.getSubject(row.getSubjectId(), token));
            }
            if (!sameBinding(row, tokens.findBangumiOauth(row.getUserId())))
                return mark(row, CollectionRemoteSyncStatus.AUTH_REQUIRED);
            if (detail.getInterest() == null || !matches(body, remoteFields(detail))) {
                return mark(row, CollectionRemoteSyncStatus.CONFLICT);
            }
            row.setBgmInterestId(detail.getInterest().getId());
            row.setBgmUpdatedAt(detail.getInterest().getUpdatedAt());
            row.setSyncTime(LocalDateTime.now());
            row.setPendingPayload(null);
            row.setRemoteBaseline(null);
            row.setNextRetryAt(null);
            return mark(row, CollectionRemoteSyncStatus.SYNCED);
        } catch (LoginExpiredException e) {
            return mark(row, CollectionRemoteSyncStatus.AUTH_REQUIRED);
        } catch (RuntimeException e) {
            if (row.getPendingPayload() == null) row.setPendingPayload(savedPayload);
            if (row.getRemoteBaseline() == null) row.setRemoteBaseline(savedBaseline);
            // 保留本地写入和上传意图，但不记录私有数据或令牌。
            log.warn("收藏上传待重试 userId={} subjectId={} error={}",
                    row.getUserId(), row.getSubjectId(), e.getClass().getSimpleName());
            int attempt = (row.getRetryCount() == null ? 0 : row.getRetryCount()) + 1;
            row.setRetryCount(attempt);
            row.setNextRetryAt(LocalDateTime.now().plusSeconds(Math.min(3600, 30L << Math.min(attempt, 7))));
            row.setRemoteSyncStatus(CollectionRemoteSyncStatus.PENDING);
            try { persist(row); }
            catch (RuntimeException storeError) {
                log.warn("收藏上传状态暂未回写 subjectId={}", row.getSubjectId());
            }
            return result(row);
        }
    }

    public CollectionUpdateVo retry(Long userId, int subjectId) {
        return lock.execute(userId, subjectId, () -> {
            var row = writer.find(userId, subjectId);
            if (row == null) throw new IllegalArgumentException("收藏不存在");
            // 冲突和账号绑定变化需要用户重新确认；重试不能覆盖这些状态。
            if (CollectionRemoteSyncStatus.AUTH_REQUIRED == row.getRemoteSyncStatus()
                    && sameBinding(row, tokens.findBangumiOauth(userId))) {
                row.setRemoteSyncStatus(CollectionRemoteSyncStatus.PENDING);
            }
            return upload(row, true);
        });
    }

    @Scheduled(initialDelayString = "${anime-flow.collection-upload.initial-delay-ms:60000}",
            fixedDelayString = "${anime-flow.collection-upload.retry-delay-ms:60000}")
    public void retryPending() {
        for (var row : mapper.selectPendingUploads()) {
            try { retry(row.getUserId(), row.getSubjectId()); }
            catch (RuntimeException e) {
                log.debug("收藏上传留待下轮 subjectId={}", row.getSubjectId());
            }
        }
    }

    private boolean sameBinding(UserBgmCollectionEntity row, UserOauthEntity oauth) {
        return oauth != null && row.getSyncOauthId() != null
                && Objects.equals(row.getSyncOauthId(), oauth.getId())
                && Objects.equals(row.getBgmAccountUid(), oauth.getPlatformUid());
    }

    private CollectionUpdateVo mark(UserBgmCollectionEntity row, CollectionRemoteSyncStatus status) {
        row.setRemoteSyncStatus(status);
        persist(row);
        return result(row);
    }

    private void persist(UserBgmCollectionEntity row) {
        if (mapper.updateRemoteState(row) != 1) throw new IllegalStateException("收藏版本已变化");
    }

    public static CollectionUpdateVo result(UserBgmCollectionEntity row) {
        return new CollectionUpdateVo(true, row.getRemoteSyncStatus(),
                row.getVersion() == null ? 0 : row.getVersion());
    }

    private UpdateCollectionBody remoteFields(SubjectDetailDto detail) {
        if (detail == null) throw new IllegalStateException("Bangumi 条目响应为空");
        var result = new UpdateCollectionBody();
        var interest = detail.getInterest();
        if (interest != null) {
            result.setType(interest.getType());
            result.setRate(interest.getRate());
            result.setComment(interest.getComment());
            result.setTags(interest.getTags());
            result.setPrivate_(interest.getPrivately());
        }
        return result;
    }

    private boolean matches(UpdateCollectionBody expected, UpdateCollectionBody actual) {
        JsonNode expectedJson = json.valueToTree(expected), actualJson = json.valueToTree(actual);
        for (var field : expectedJson.properties()) {
            if (!CollectionFieldComparison.same(field.getKey(), field.getValue(), actualJson.get(field.getKey()))) return false;
        }
        return true;
    }

    private boolean sameEditedFields(UpdateCollectionBody edits, UpdateCollectionBody before, UpdateCollectionBody now) {
        JsonNode editJson = json.valueToTree(edits), beforeJson = json.valueToTree(before), nowJson = json.valueToTree(now);
        var fields = editJson.fieldNames();
        while (fields.hasNext()) {
            String name = fields.next();
            if (!CollectionFieldComparison.same(name, beforeJson.get(name), nowJson.get(name))) return false;
        }
        return true;
    }
}
