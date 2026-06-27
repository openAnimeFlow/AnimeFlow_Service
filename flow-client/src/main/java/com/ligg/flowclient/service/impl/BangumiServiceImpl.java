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
import com.ligg.api.bangumiv0api.BangumiV0Client;
import com.ligg.common.constants.Constants;
import com.ligg.common.entity.BangumiEpisodeEntity;
import com.ligg.common.entity.BangumiSubjectEntity;
import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.exception.LoginExpiredException;
import com.ligg.common.model.CoverImages;
import com.ligg.common.thirdparty.bangumi.enums.SubjectImageType;
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
import com.ligg.flowclient.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class BangumiServiceImpl implements BangumiService {

    private final BangumiEpisodeMapper episodeMapper;
    private final BangumiSubjectMapper subjectMapper;
    private final BangumiClient bangumiClient;
    private final BangumiV0Client bangumiV0Client;
    private final JwtTokenService jwtTokenService;
    private final BangumiOAuthTokenService bangumiOAuthTokenService;
    private final BangumiOAuthExecutor bangumiOAuthExecutor;
    private final ObjectMapper objectMapper;

    /**
     * 并发控制：防止同一 subject 的图片被重复请求 API。
     * key = relatedSubjectId, value = 锁对象
     */
    private final ConcurrentHashMap<Integer, Object> imageFetchLocks = new ConcurrentHashMap<>();

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
    public SubjectRelationsVo getRelatedSubjects(Integer subjectId, int limit, int offset) {
        SubjectRelationsVo vo = new SubjectRelationsVo();
        if (subjectId == null || limit <= 0) {
            vo.setData(Collections.emptyList());
            vo.setTotal(0);
            return vo;
        }

        IPage<SubjectRelationRow> page = subjectMapper.selectRelatedSubjects(
                new LimitOffsetPage<>(limit, offset), subjectId);

        List<SubjectRelationRow> rows = page.getRecords();
        if (rows == null || rows.isEmpty()) {
            vo.setData(Collections.emptyList());
            vo.setTotal((int) page.getTotal());
            return vo;
        }

        List<SubjectRelationsVo.Item> items = rows.stream().map(this::toRelationItem).toList();
        vo.setData(items);
        vo.setTotal((int) page.getTotal());
        return vo;
    }

    /**
     * 将 DB 行转为 VO 条目，在转换过程中按需获取并回写图片。
     */
    private SubjectRelationsVo.Item toRelationItem(SubjectRelationRow row) {
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

        // 处理封面图
        CoverImages images = resolveImages(row);
        subject.setImages(images);
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

    /**
     * 解析图片：如果 DB 中已有则直接解析 JSON，否则调用 Bangumi API 获取并回写。
     */
    private CoverImages resolveImages(SubjectRelationRow row) {
        // DB 已有图片数据，直接解析
        if (StringUtils.hasText(row.getImages()) && !"null".equals(row.getImages())) {
            try {
                return objectMapper.readValue(row.getImages(), CoverImages.class);
            } catch (JsonProcessingException e) {
                log.warn("解析条目图片 JSON 失败, subjectId={}", row.getId(), e);
            }
        }

        // 无图片数据，通过 API 获取
        return fetchAndSaveImages(row.getId());
    }

    /**
     * 调用 Bangumi V0 API 获取五种规格封面图，写回 DB。
     * 使用 ConcurrentHashMap 中的锁对象防止同一 subject 并发重复请求。
     *
     * @param subjectId 条目 ID
     * @return 封面图片，获取失败则返回空 CoverImages
     */
    private CoverImages fetchAndSaveImages(int subjectId) {
        Object lock = imageFetchLocks.computeIfAbsent(subjectId, k -> new Object());
        synchronized (lock) {
            try {
                // 双重检查：获取锁后再次检查 DB 中是否已有图片
                BangumiSubjectEntity entity = subjectMapper.selectById(subjectId);
                if (entity != null && StringUtils.hasText(entity.getImages())
                        && !"null".equals(entity.getImages())) {
                    try {
                        return objectMapper.readValue(entity.getImages(), CoverImages.class);
                    } catch (JsonProcessingException e) {
                        log.warn("双重检查时解析条目图片 JSON 失败, subjectId={}", subjectId, e);
                    }
                }

                CoverImages images = new CoverImages();
                boolean anySucceeded = false;

                for (SubjectImageType type : SubjectImageType.values()) {
                    try {
                        String url = bangumiV0Client.getSubjectImageUrl(subjectId, type);
                        if (StringUtils.hasText(url)) {
                            setImageField(images, type, url);
                            anySucceeded = true;
                        }
                    } catch (RuntimeException e) {
                        log.warn("获取条目图片失败, subjectId={}, type={}", subjectId, type.getValue(), e);
                    }
                }

                if (anySucceeded) {
                    try {
                        String imagesJson = objectMapper.writeValueAsString(images);
                        BangumiSubjectEntity updateEntity = new BangumiSubjectEntity();
                        updateEntity.setId(subjectId);
                        updateEntity.setImages(imagesJson);
                        subjectMapper.updateById(updateEntity);
                        log.info("条目图片已回写 DB, subjectId={}, images={}", subjectId, imagesJson);
                    } catch (JsonProcessingException e) {
                        log.error("序列化条目图片 JSON 失败, subjectId={}", subjectId, e);
                    }
                } else {
                    log.warn("条目图片获取全部失败, subjectId={}", subjectId);
                }
                return images;
            } finally {
                imageFetchLocks.remove(subjectId);
            }
        }
    }

    private static void setImageField(CoverImages images, SubjectImageType type, String url) {
        switch (type) {
            case LARGE -> images.setLarge(url);
            case COMMON -> images.setCommon(url);
            case MEDIUM -> images.setMedium(url);
            case SMALL -> images.setSmall(url);
            case GRID -> images.setGrid(url);
        }
    }
}
