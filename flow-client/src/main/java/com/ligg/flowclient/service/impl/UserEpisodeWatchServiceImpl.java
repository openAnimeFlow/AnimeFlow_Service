package com.ligg.flowclient.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ligg.common.entity.BangumiEpisodeEntity;
import com.ligg.common.entity.UserBgmCollectionEntity;
import com.ligg.common.entity.UserEpisodeWatchEntity;
import com.ligg.flowclient.mapper.BangumiEpisodeMapper;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.mapper.UserEpisodeWatchMapper;
import com.ligg.flowclient.module.dto.UpdateEpisodeWatchDto;
import com.ligg.flowclient.module.vo.SubjectEpisodeWatchStatusVo;
import com.ligg.flowclient.service.JwtTokenService;
import com.ligg.flowclient.service.UserEpisodeWatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserEpisodeWatchServiceImpl implements UserEpisodeWatchService {

    private static final int WATCH_STATUS_WATCHED = 1;
    private static final int EPISODE_TYPE_MAIN = 0;

    private final JwtTokenService jwtTokenService;
    private final BangumiEpisodeMapper bangumiEpisodeMapper;
    private final UserEpisodeWatchMapper userEpisodeWatchMapper;
    private final UserBgmCollectionMapper userBgmCollectionMapper;

    @Override
    public void updateEpisodeWatch(String accessToken, int episodeId, UpdateEpisodeWatchDto dto) {
        Long userId = jwtTokenService.validateAccessToken(accessToken);
        BangumiEpisodeEntity episode = bangumiEpisodeMapper.selectById(episodeId);
        if (episode == null) {
            throw new IllegalArgumentException("剧集不存在");
        }

        if (Boolean.TRUE.equals(dto.getWatched())) {
            userEpisodeWatchMapper.upsertWatched(userId, episode.getSubjectId(), episode.getId());
        } else {
            userEpisodeWatchMapper.markUnwatched(userId, episodeId);
        }

        refreshCollectionEpisodeProgress(userId, episode.getSubjectId());
    }

    @Override
    public SubjectEpisodeWatchStatusVo getSubjectWatchStatus(String accessToken, int subjectId) {
        Long userId = jwtTokenService.validateAccessToken(accessToken);
        List<Long> watchedEpisodeIds = listWatchedEpisodeIds(userId, subjectId).stream().toList();
        return new SubjectEpisodeWatchStatusVo(subjectId, watchedEpisodeIds);
    }

    @Override
    public Set<Long> listWatchedEpisodeIds(long userId, int subjectId) {
        if (userId <= 0 || subjectId <= 0) {
            return Collections.emptySet();
        }
        List<UserEpisodeWatchEntity> rows = userEpisodeWatchMapper.selectList(
                new LambdaQueryWrapper<UserEpisodeWatchEntity>()
                        .select(UserEpisodeWatchEntity::getEpisodeId)
                        .eq(UserEpisodeWatchEntity::getUserId, userId)
                        .eq(UserEpisodeWatchEntity::getSubjectId, subjectId)
                        .eq(UserEpisodeWatchEntity::getWatchStatus, WATCH_STATUS_WATCHED)
                        .orderByAsc(UserEpisodeWatchEntity::getEpisodeId));
        Set<Long> watchedEpisodeIds = new LinkedHashSet<>(rows.size());
        for (UserEpisodeWatchEntity row : rows) {
            watchedEpisodeIds.add(row.getEpisodeId().longValue());
        }
        return watchedEpisodeIds;
    }

    /**
     * 维护 user_bgm_collection.ep_status，表示按正片顺序连续看完的话数。
     */
    private void refreshCollectionEpisodeProgress(Long userId, Integer subjectId) {
        UserBgmCollectionEntity collection = userBgmCollectionMapper.selectOne(
                new LambdaQueryWrapper<UserBgmCollectionEntity>()
                        .eq(UserBgmCollectionEntity::getUserId, userId)
                        .eq(UserBgmCollectionEntity::getSubjectId, subjectId)
                        .last("LIMIT 1"));
        if (collection == null) {
            return;
        }

        List<BangumiEpisodeEntity> mainEpisodes = bangumiEpisodeMapper.selectList(
                new LambdaQueryWrapper<BangumiEpisodeEntity>()
                        .select(BangumiEpisodeEntity::getId, BangumiEpisodeEntity::getSort)
                        .eq(BangumiEpisodeEntity::getSubjectId, subjectId)
                        .eq(BangumiEpisodeEntity::getType, EPISODE_TYPE_MAIN)
                        .orderByAsc(BangumiEpisodeEntity::getSort)
                        .orderByAsc(BangumiEpisodeEntity::getId));

        Set<Long> watchedEpisodeIds = listWatchedEpisodeIds(userId, subjectId);
        int contiguousWatchedCount = 0;
        for (BangumiEpisodeEntity mainEpisode : mainEpisodes) {
            if (!watchedEpisodeIds.contains(mainEpisode.getId().longValue())) {
                break;
            }
            contiguousWatchedCount++;
        }

        userBgmCollectionMapper.update(
                null,
                new LambdaUpdateWrapper<UserBgmCollectionEntity>()
                        .eq(UserBgmCollectionEntity::getId, collection.getId())
                        .set(UserBgmCollectionEntity::getEpStatus, contiguousWatchedCount));
    }
}
