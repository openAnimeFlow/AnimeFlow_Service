/**
 * @author Ligg
 * @date 2026/6/15 19:01
 */
package com.ligg.flowclient.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.entity.BangumiEpisodeEntity;
import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.exception.LoginExpiredException;
import com.ligg.common.thirdparty.bangumi.request.SearchSubjectsBody;
import com.ligg.common.thirdparty.bangumi.response.SubjectEpisodesDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectsDto;
import com.ligg.common.vo.bangumi.SearchSuggestionsVo;
import com.ligg.flowclient.mapper.BangumiEpisodeMapper;
import com.ligg.flowclient.mapper.BangumiSubjectMapper;
import com.ligg.flowclient.module.dto.SearchSuggestionRow;
import com.ligg.flowclient.mybatis.LimitOffsetPage;
import com.ligg.flowclient.service.BangumiOAuthExecutor;
import com.ligg.flowclient.service.BangumiOAuthTokenService;
import com.ligg.flowclient.service.BangumiService;
import com.ligg.flowclient.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BangumiServiceImpl implements BangumiService {

    private final BangumiEpisodeMapper episodeMapper;
    private final BangumiSubjectMapper subjectMapper;
    private final BangumiClient bangumiClient;
    private final JwtTokenService jwtTokenService;
    private final BangumiOAuthTokenService bangumiOAuthTokenService;
    private final BangumiOAuthExecutor bangumiOAuthExecutor;

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

    @Override
    public SearchSuggestionsVo getSearchSuggestions(String keyword, int type, int limit) {
        SearchSuggestionsVo vo = new SearchSuggestionsVo();
        if (!StringUtils.hasText(keyword) || limit <= 0) {
            return vo;
        }

        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.isEmpty()) {
            return vo;
        }

        List<SearchSuggestionRow> rows = subjectMapper.selectSearchSuggestions(trimmedKeyword, type, limit);
        if (rows == null || rows.isEmpty()) {
            return vo;
        }

        vo.setData(rows.stream().map(this::toSuggestionItem).toList());
        return vo;
    }

    private SearchSuggestionsVo.Item toSuggestionItem(SearchSuggestionRow row) {
        SearchSuggestionsVo.Item item = new SearchSuggestionsVo.Item();
        item.setId(row.getId());
        item.setName(row.getName());
        item.setNameCn(row.getNameCn());
        return item;
    }

    @Override
    public SubjectsDto searchSubjects(SearchSubjectsBody body, int limit, int offset, String flowAccessToken) {
        if (StringUtils.hasText(flowAccessToken)) {
            try {
                Long userId = jwtTokenService.validateAccessToken(flowAccessToken);
                UserOauthEntity oauth = bangumiOAuthTokenService.findBangumiOauth(userId);
                if (oauth != null) {
                    return bangumiOAuthExecutor.execute(oauth,
                            bangumiToken -> bangumiClient.searchSubjects(body, limit, offset, bangumiToken));
                }
            } catch (LoginExpiredException ignored) {
                // Flow JWT 无效或未绑定 Bangumi，回退匿名搜索
            }
        }
        return bangumiClient.searchSubjects(body, limit, offset, null);
    }
}
