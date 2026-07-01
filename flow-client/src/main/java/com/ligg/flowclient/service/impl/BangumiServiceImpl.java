/**
 * @author Ligg
 * @date 2026/6/15 19:01
 */
package com.ligg.flowclient.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.constants.Constants;
import com.ligg.common.entity.BangumiEpisodeEntity;
import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.exception.LoginExpiredException;
import com.ligg.common.thirdparty.bangumi.model.BangumiRating;
import com.ligg.common.thirdparty.bangumi.model.BangumiSubject;
import com.ligg.common.thirdparty.bangumi.request.SearchSubjectsBody;
import com.ligg.common.thirdparty.bangumi.response.SubjectEpisodesDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectRelationsDto.RelationInfo;
import com.ligg.common.thirdparty.bangumi.response.SubjectsDto;
import com.ligg.common.utils.InfoboxParser;
import com.ligg.common.vo.bangumi.SearchSuggestionsVo;
import com.ligg.common.vo.bangumi.SubjectRelationsVo;
import com.ligg.flowclient.mapper.BangumiEpisodeMapper;
import com.ligg.flowclient.mapper.BangumiSubjectMapper;
import com.ligg.flowclient.module.dto.SearchSuggestionRow;
import com.ligg.flowclient.module.dto.SubjectRelationRow;
import com.ligg.flowclient.mybatis.LimitOffsetPage;
import com.ligg.flowclient.service.BangumiOAuthExecutor;
import com.ligg.flowclient.service.BangumiOAuthTokenService;
import com.ligg.flowclient.service.BangumiService;
import com.ligg.flowclient.service.ImageBackfillService;
import com.ligg.flowclient.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class BangumiServiceImpl implements BangumiService {

    private final BangumiEpisodeMapper episodeMapper;
    private final BangumiSubjectMapper subjectMapper;
    private final BangumiClient bangumiClient;
    private final JwtTokenService jwtTokenService;
    private final BangumiOAuthTokenService bangumiOAuthTokenService;
    private final BangumiOAuthExecutor bangumiOAuthExecutor;
    private final ObjectMapper objectMapper;
    private final ImageBackfillService imageBackfillService;

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

    /**
     * 获取关联条目。
     */
    @Override
    public SubjectRelationsVo getRelatedSubjects(Integer subjectId, int limit, int offset, int type, String bangumiAccessToken) {
        SubjectRelationsVo vo = new SubjectRelationsVo();
        if (subjectId == null || limit <= 0) {
            vo.setData(Collections.emptyList());
            vo.setTotal(0);
            return vo;
        }

        IPage<SubjectRelationRow> page = subjectMapper.selectRelatedSubjects(
                new LimitOffsetPage<>(limit, offset), subjectId, type);

        List<SubjectRelationRow> rows = page.getRecords();
        if (rows == null || rows.isEmpty()) {
            vo.setData(Collections.emptyList());
            vo.setTotal((int) page.getTotal());
            return vo;
        }

        List<SubjectRelationsVo.Item> items = rows.stream().map(row -> toRelationItem(row, bangumiAccessToken)).toList();
        vo.setData(items);
        vo.setTotal((int) page.getTotal());
        return vo;
    }

    /**
     * 将 DB 行转为 VO 条目，在转换过程中按需获取并回写图片。
     */
    private SubjectRelationsVo.Item toRelationItem(SubjectRelationRow row, String bangumiAccessToken) {
        SubjectRelationsVo.Item item = new SubjectRelationsVo.Item();

        // 构建关联信息
        RelationInfo relation = new RelationInfo();
        relation.setId(row.getRelationType());
        relation.setDesc("");
        relation.setJp("");
        relation.setEn("");
        relation.setCn(Constants.RELATION_TYPE_CN.getOrDefault(row.getRelationType(), ""));
        item.setRelation(relation);
        item.setOrder(row.getOrder());

        // 构建条目信息
        BangumiSubject subject = new BangumiSubject();
        subject.setId(row.getId());
        subject.setName(row.getName());
        subject.setNameCN(row.getNameCn());
        subject.setType(row.getType());
        subject.setNsfw(row.getNsfw());
        subject.setInfo(InfoboxParser.toInfo(row.getInfobox()));
        subject.setRating(buildRating(row));
        subject.setLocked(false);
        subject.setImages(imageBackfillService.resolve(row.getImages(), row.getId(), bangumiAccessToken));

        item.setSubject(subject);
        return item;
    }

    /**
     * 从 DB 行构建评分信息。count 来自 score_details JSON 的 1-10 分值对应的计数。
     */
    private BangumiRating buildRating(SubjectRelationRow row) {
        BangumiRating rating = new BangumiRating();
        rating.setRank(row.getRank());
        rating.setScore(row.getScore());

        List<Integer> counts = Collections.emptyList();
        String scoreDetailsJson = row.getScoreDetails();
        if (StringUtils.hasText(scoreDetailsJson)) {
            try {
                Map<String, Integer> map = objectMapper.readValue(scoreDetailsJson,
                        new TypeReference<>() {});
                counts = IntStream.rangeClosed(1, 10)
                        .mapToObj(i -> map.getOrDefault(String.valueOf(i), 0))
                        .toList();
            } catch (JsonProcessingException e) {
                log.warn("解析 score_details JSON 失败: {}", scoreDetailsJson, e);
            }
        }
        rating.setCount(counts);
        rating.setTotal(counts.stream().mapToInt(Integer::intValue).sum());
        return rating;
    }
}
