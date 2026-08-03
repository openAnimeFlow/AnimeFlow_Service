package com.ligg.flowclient.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.entity.BangumiEpisodeEntity;
import com.ligg.common.entity.UserPlayHistoryEntity;
import com.ligg.flowclient.mapper.BangumiEpisodeMapper;
import com.ligg.flowclient.mapper.UserPlayHistoryMapper;
import com.ligg.flowclient.module.dto.SavePlayHistoryDto;
import com.ligg.flowclient.module.dto.UserPlayHistoryRow;
import com.ligg.flowclient.module.vo.PlayHistoryVo;
import com.ligg.flowclient.service.JwtTokenService;
import com.ligg.flowclient.service.UserPlayHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserPlayHistoryServiceImpl implements UserPlayHistoryService {

    private static final TypeReference<List<String>> ALIAS_TYPE = new TypeReference<>() {};

    private final JwtTokenService jwtTokenService;
    private final BangumiEpisodeMapper bangumiEpisodeMapper;
    private final UserPlayHistoryMapper userPlayHistoryMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void save(String accessToken, SavePlayHistoryDto dto) {
        Long userId = jwtTokenService.validateAccessToken(accessToken);
        BangumiEpisodeEntity episode = bangumiEpisodeMapper.selectById(dto.getEpisodeId());
        if (episode == null) {
            throw new IllegalArgumentException("剧集不存在");
        }
        if (!dto.getSubjectId().equals(episode.getSubjectId())) {
            throw new IllegalArgumentException("剧集不属于指定番剧");
        }
        if (dto.getPositionSeconds() > dto.getDurationSeconds()) {
            throw new IllegalArgumentException("播放进度不能超过视频时长");
        }

        UserPlayHistoryEntity row = new UserPlayHistoryEntity();
        row.setUserId(userId);
        row.setSubjectId(dto.getSubjectId());
        row.setEpisodeId(dto.getEpisodeId());
        row.setEpisodeSort(episode.getSort());
        row.setSubjectName(dto.getSubjectName().trim());
        row.setCover(dto.getCover().trim());
        row.setAlias(toJson(dto.getAlias()));
        row.setPositionSeconds(dto.getPositionSeconds());
        row.setDurationSeconds(dto.getDurationSeconds());
        userPlayHistoryMapper.upsert(row);
    }

    @Override
    public List<PlayHistoryVo> list(String accessToken, int limit, int offset) {
        Long userId = jwtTokenService.validateAccessToken(accessToken);
        int normalizedLimit = Math.min(Math.max(limit, 1), 100);
        int normalizedOffset = Math.max(offset, 0);
        return userPlayHistoryMapper.selectPageByUser(userId, normalizedLimit, normalizedOffset)
                .stream().map(this::toVo).toList();
    }

    @Override
    public PlayHistoryVo getBySubject(String accessToken, int subjectId) {
        Long userId = jwtTokenService.validateAccessToken(accessToken);
        UserPlayHistoryRow row = userPlayHistoryMapper.selectByUserAndSubject(userId, subjectId);
        return row == null ? null : toVo(row);
    }

    @Override
    public void clearProgress(String accessToken, int subjectId) {
        Long userId = jwtTokenService.validateAccessToken(accessToken);
        userPlayHistoryMapper.clearProgress(userId, subjectId);
    }

    private PlayHistoryVo toVo(UserPlayHistoryRow row) {
        PlayHistoryVo vo = new PlayHistoryVo();
        vo.setId(row.getId());
        vo.setSubjectId(row.getSubjectId());
        vo.setEpisodeId(row.getEpisodeId());
        vo.setEpisodeSort(row.getEpisodeSort());
        vo.setSubjectName(row.getSubjectName());
        vo.setCover(row.getCover());
        vo.setAlias(parseAlias(row.getAlias()));
        vo.setPositionSeconds(row.getPositionSeconds());
        vo.setDurationSeconds(row.getDurationSeconds());
        vo.setCompleted(Boolean.TRUE.equals(row.getIsCompleted()));
        vo.setCompletedAt(row.getCompletedAt());
        vo.setLastPlayedAt(row.getLastPlayedAt());
        return vo;
    }

    private String toJson(List<String> alias) {
        try {
            return objectMapper.writeValueAsString(alias == null ? Collections.emptyList() : alias);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("别名格式无效", e);
        }
    }

    private List<String> parseAlias(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<String> alias = objectMapper.readValue(json, ALIAS_TYPE);
            return alias == null ? Collections.emptyList() : alias;
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
