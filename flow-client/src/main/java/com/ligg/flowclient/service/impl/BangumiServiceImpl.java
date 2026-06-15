/**
 * @author Ligg
 * @date 2026/6/15 19:01
 */
package com.ligg.flowclient.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ligg.common.entity.BangumiEpisodeEntity;
import com.ligg.common.thirdparty.bangumi.response.SubjectEpisodesDto;
import com.ligg.flowclient.mapper.BangumiEpisodeMapper;
import com.ligg.flowclient.mybatis.LimitOffsetPage;
import com.ligg.flowclient.service.BangumiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class BangumiServiceImpl implements BangumiService {

    private final BangumiEpisodeMapper episodeMapper;

    @Override
    public SubjectEpisodesDto getEpisodes(Integer subjectId, int limit, int offset) {
        SubjectEpisodesDto dto = new SubjectEpisodesDto();
        if (subjectId == null) {
            dto.setData(Collections.emptyList());
            dto.setTotal(0);
            return dto;
        }

        if (limit <= 0) {
            dto.setData(Collections.emptyList());
            dto.setTotal(0);
            return dto;
        }

        LambdaQueryWrapper<BangumiEpisodeEntity> wrapper = new LambdaQueryWrapper<BangumiEpisodeEntity>()
                .eq(BangumiEpisodeEntity::getSubjectId, subjectId)
                .orderByAsc(BangumiEpisodeEntity::getSort);

        IPage<BangumiEpisodeEntity> page = episodeMapper.selectPage(new LimitOffsetPage<>(limit, offset), wrapper);

        dto.setTotal((int) page.getTotal());
        dto.setData(page.getRecords().stream().map(this::toEpisode).toList());
        return dto;
    }

    private SubjectEpisodesDto.Episode toEpisode(BangumiEpisodeEntity entity) {
        SubjectEpisodesDto.Episode episode = new SubjectEpisodesDto.Episode();
        episode.setId(entity.getId().longValue());
        episode.setSubjectId(entity.getSubjectId());
        episode.setSort(entity.getSort());
        episode.setType(entity.getType());
        episode.setDisc(entity.getDisc());
        episode.setName(entity.getName());
        episode.setNameCN(entity.getNameCn());
        episode.setDuration(entity.getDuration());
        episode.setAirdate(entity.getAirdate());
        episode.setDesc(entity.getDescription());
        return episode;
    }
}
