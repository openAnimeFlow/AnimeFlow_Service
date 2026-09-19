package com.ligg.flowclient.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.entity.UserBgmCollectionEntity;
import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.thirdparty.bangumi.request.UpdateCollectionBody;
import com.ligg.flowclient.mapper.BangumiSubjectMapper;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.module.dto.UpdateUserCollectionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/** A short local transaction; callers hold CollectionWriteLock, never a transaction across HTTP. */
@Service
@RequiredArgsConstructor
public class LocalCollectionWriter {
    private final UserBgmCollectionMapper mapper;
    private final BangumiSubjectMapper subjectMapper;
    private final ObjectMapper json;

    public UserBgmCollectionEntity find(Long userId, int subjectId) {
        return mapper.selectOne(new LambdaQueryWrapper<UserBgmCollectionEntity>()
                .eq(UserBgmCollectionEntity::getUserId, userId)
                .eq(UserBgmCollectionEntity::getSubjectId, subjectId));
    }

    @Transactional
    public UserBgmCollectionEntity save(Long userId, int subjectId, UpdateUserCollectionDto dto,
                                        UserOauthEntity oauth) {
        if (subjectId <= 0 || !dto.hasUpdateField()) {
            throw new IllegalArgumentException("条目 ID 无效或没有需要更新的收藏字段");
        }
        if (dto.getProgress() != null) {
            throw new IllegalArgumentException("收藏接口暂不支持 progress，请使用剧集观看状态接口");
        }
        if (dto.getType() != null && (dto.getType() < 1 || dto.getType() > 5)
                || dto.getRate() != null && (dto.getRate() < 0 || dto.getRate() > 10)) {
            throw new IllegalArgumentException("收藏分类或评分无效");
        }
        var row = find(userId, subjectId);
        boolean isNew = row == null;
        if (isNew && dto.getType() == null) {
            throw new IllegalArgumentException("首次收藏请选择收藏分类");
        }
        var subject = subjectMapper.selectById(subjectId);
        Integer subjectType = subject != null ? subject.getType()
                : row != null ? row.getSubjectType() : dto.getSubjectType();
        if (subjectType == null || !Set.of(1, 2, 3, 4, 6).contains(subjectType)
                || dto.getSubjectType() != null && !dto.getSubjectType().equals(subjectType)) {
            throw new IllegalArgumentException("条目大类无效或与条目不一致");
        }
        if (isNew) {
            row = new UserBgmCollectionEntity();
            row.setUserId(userId);
            row.setSubjectId(subjectId);
            row.setSubjectType(subjectType);
            row.setRate(0);
            row.setComment("");
            row.setTags("[]");
            row.setEpStatus(0);
            row.setVolStatus(0);
            row.setIsPrivate(false);
            row.setCreateTime(LocalDateTime.now());
            row.setVersion(0L);
        }
        UpdateCollectionBody pending = readPayload(row.getPendingPayload());
        row.setSubjectType(subjectType);
        if (dto.getType() != null) { row.setType(dto.getType()); pending.setType(dto.getType()); }
        if (dto.getRate() != null) { row.setRate(dto.getRate()); pending.setRate(dto.getRate()); }
        if (dto.getComment() != null) { row.setComment(dto.getComment()); pending.setComment(dto.getComment()); }
        if (dto.getTags() != null) { row.setTags(writeJson(dto.getTags())); pending.setTags(dto.getTags()); }
        if (dto.getPrivate_() != null) { row.setIsPrivate(dto.getPrivate_()); pending.setPrivate_(dto.getPrivate_()); }
        row.setVersion((row.getVersion() == null ? 0 : row.getVersion()) + 1);
        row.setLocalUpdatedAt(LocalDateTime.now());
        row.setPendingPayload(writeJson(pending));
        row.setRemoteSyncStatus(oauth == null ? "LOCAL_ONLY" : "PENDING");
        if (mapper.countBlockedSyncItems(userId, subjectId) > 0) row.setRemoteSyncStatus("CONFLICT");
        row.setSyncOauthId(oauth == null ? null : oauth.getId());
        row.setBgmAccountUid(oauth == null ? null : oauth.getPlatformUid());
        row.setRemoteBaseline(null);
        row.setRetryCount(0);
        row.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
        if (isNew) {
            mapper.insert(row);
        } else {
            if (mapper.updateLocal(row) != 1) throw new IllegalStateException("收藏保存失败，请重试");
        }
        return row;
    }

    public UpdateCollectionBody readPayload(String value) {
        if (value == null) return new UpdateCollectionBody();
        try { return json.readValue(value, UpdateCollectionBody.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("收藏待同步数据无效", e); }
    }

    public String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("收藏序列化失败", e); }
    }
}
