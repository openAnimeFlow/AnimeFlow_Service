/**
 * @author Ligg
 * @date 2026/6/15 19:01
 */
package com.ligg.flowclient.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.constants.Constants;
import com.ligg.common.entity.BangumiEpisodeEntity;
import com.ligg.common.entity.BangumiSubjectEntity;
import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.exception.LoginExpiredException;
import com.ligg.common.model.CoverImages;
import com.ligg.common.thirdparty.bangumi.model.BangumiRating;
import com.ligg.common.thirdparty.bangumi.model.BangumiSubject;
import com.ligg.common.thirdparty.bangumi.request.SearchSubjectsBody;
import com.ligg.common.thirdparty.bangumi.response.SubjectDetailDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectEpisodesDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectRelationsDto.RelationInfo;
import com.ligg.common.thirdparty.bangumi.response.SubjectsDto;
import com.ligg.common.utils.InfoboxParser;
import com.ligg.common.utils.Utils;
import com.ligg.common.vo.bangumi.SearchSuggestionsVo;
import com.ligg.common.vo.bangumi.SubjectDetailVo;
import com.ligg.common.vo.bangumi.SubjectRelationsVo;
import com.ligg.flowclient.mapper.BangumiEpisodeMapper;
import com.ligg.flowclient.mapper.BangumiSubjectMapper;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.module.dto.SearchSuggestionRow;
import com.ligg.flowclient.module.dto.SubjectRelationRow;
import com.ligg.flowclient.module.dto.UserSubjectInterestRow;
import com.ligg.flowclient.mybatis.LimitOffsetPage;
import com.ligg.flowclient.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;

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
    private final UserBgmCollectionMapper userBgmCollectionMapper;
    private final UserEpisodeWatchService userEpisodeWatchService;

    /**
     * 条目详情
     */
    @Override
    public SubjectDetailVo getSubjectInfo(Integer subjectId, long userId) {
        BangumiSubjectEntity subject = subjectMapper.selectById(subjectId);
        if (subject == null) {
            return new SubjectDetailVo();
        }

        SubjectDetailVo vo = new SubjectDetailVo();
        vo.setId(subject.getId());
        vo.setName(subject.getName());
        vo.setNameCN(subject.getNameCn());
        vo.setType(subject.getType());
        vo.setNsfw(subject.getNsfw());
        vo.setSummary(subject.getSummary());
        vo.setSeries(subject.getSeries());
        vo.setInfo(InfoboxParser.toInfo(subject.getInfobox()));

        // 临时处理
        vo.setRedirect(0);
        vo.setSeriesEntry(0);
        vo.setVolumes(0);
        vo.setLocked(false);

        // 结构化 infobox 条目
        if (StringUtils.hasText(subject.getInfobox())) {
            Map<String, List<String>> entries = InfoboxParser.toEntries(subject.getInfobox());
            List<SubjectDetailDto.InfoboxEntry> infoboxList = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : entries.entrySet()) {
                SubjectDetailDto.InfoboxEntry ie = new SubjectDetailDto.InfoboxEntry();
                ie.setKey(entry.getKey());
                ie.setValues(entry.getValue().stream()
                        .map(v -> {
                            SubjectDetailDto.InfoboxValue iv = new SubjectDetailDto.InfoboxValue();
                            iv.setV(v);
                            return iv;
                        })
                        .toList());
                infoboxList.add(ie);
            }
            vo.setInfobox(infoboxList);
        }

        long eps = episodeMapper.selectCount(
                new LambdaQueryWrapper<BangumiEpisodeEntity>()
                        .eq(BangumiEpisodeEntity::getSubjectId, subjectId)
                        .eq(BangumiEpisodeEntity::getType, 0));
        vo.setEps((int) eps);

        // 评分
        BangumiRating rating = new BangumiRating();
        rating.setScore(subject.getScore());
        rating.setRank(subject.getRank());
        rating.setCount(parseScoreDetails(subject.getScoreDetails()));
        rating.setTotal(rating.getCount().stream().mapToInt(Integer::intValue).sum());
        vo.setRating(rating);

        // 放送时间
        if (subject.getDate() != null) {
            try {
                LocalDate date = LocalDate.parse(subject.getDate());
                SubjectDetailDto.Airtime airtime = new SubjectDetailDto.Airtime();
                airtime.setDate(subject.getDate());
                airtime.setMonth(date.getMonthValue());
                airtime.setWeekday(date.getDayOfWeek().getValue());
                airtime.setYear(date.getYear());
                vo.setAirtime(airtime);
            } catch (java.time.format.DateTimeParseException ignored) {
                // 日期格式异常，跳过
            }
        }

        // 平台
        if (subject.getPlatform() != null) {
            SubjectDetailDto.Platform platform = new SubjectDetailDto.Platform();
            platform.setId(subject.getPlatform());
            platform.setType("TV");
            platform.setTypeCN("TV");
            platform.setAlias("tv");
            platform.setOrder(0);
            platform.setEnableHeader(true);
            platform.setWikiTpl("TVAnime");
            vo.setPlatform(platform);
        }

        // 标签
        if (StringUtils.hasText(subject.getTags())) {
            try {
                List<SubjectDetailDto.SubjectTag> tags = objectMapper.readValue(
                        subject.getTags(), new TypeReference<>() {
                        });
                vo.setTags(tags);
            } catch (JsonProcessingException e) {
                log.warn("解析 tags JSON 失败: {}", subject.getTags(), e);
            }
        }

        // 公共标签
        if (StringUtils.hasText(subject.getMetaTags())) {
            try {
                List<String> metaTags = objectMapper.readValue(
                        subject.getMetaTags(), new TypeReference<>() {
                        });
                vo.setMetaTags(metaTags);
            } catch (JsonProcessingException e) {
                log.warn("解析 meta_tags JSON 失败: {}", subject.getMetaTags(), e);
            }
        }

        // 收藏统计
        if (StringUtils.hasText(subject.getFavorite())) {
            try {
                Map<String, Integer> raw = objectMapper.readValue(
                        subject.getFavorite(), new TypeReference<>() {
                        });
                Map<String, Integer> collection = new LinkedHashMap<>();
                String[] keys = {"1", "2", "3", "4", "5"};
                String[] names = {"wish", "done", "doing", "on_hold", "dropped"};
                for (int i = 0; i < keys.length; i++) {
                    Integer count = raw.get(names[i]);
                    if (count != null) {
                        collection.put(keys[i], count);
                    }
                }
                vo.setCollection(collection);
            } catch (JsonProcessingException e) {
                log.warn("解析 favorite JSON 失败: {}", subject.getFavorite(), e);
            }
        }

        // 封面图片
        CoverImages images = imageBackfillService.resolve(subject.getImages(), subjectId, null);
        if (images != null && StringUtils.hasText(images.getLarge())) {
            Utils.applyWsrvCdnInPlace(images);
            vo.setImages(images);
        }

        if (userId > 0) {
            UserSubjectInterestRow userInterest = userBgmCollectionMapper.selectUserSubjectInterest(userId, subjectId);
            if (userInterest != null) {
                SubjectDetailDto.SubjectInterest interest = getInterest(userInterest);
                vo.setInterest(interest);
            }
        }
        return vo;
    }

    private static SubjectDetailDto.SubjectInterest getInterest(UserSubjectInterestRow userInterest) {
        SubjectDetailDto.SubjectInterest interest = new SubjectDetailDto.SubjectInterest();
        interest.setId(userInterest.getId());
        interest.setType(userInterest.getType());
        if (userInterest.getRate() != null) interest.setRate(userInterest.getRate());
        if (userInterest.getComment() != null) interest.setComment(userInterest.getComment());
        if (userInterest.getTags() != null) interest.setTags(userInterest.getTags());
        if (userInterest.getEpStatus() != null) interest.setEpStatus(userInterest.getEpStatus());
        if (userInterest.getVolStatus() != null) interest.setVolStatus(userInterest.getVolStatus());
        if (userInterest.getPrivately() != null) interest.setPrivately(userInterest.getPrivately());
        interest.setUpdatedAt(userInterest.getBgmUpdatedAt());
        return interest;
    }

    @Override
    public SubjectEpisodesDto getEpisodes(Integer subjectId, int limit, int offset, long userId) {
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
        Set<Long> watchedEpisodeIds = userId > 0
                ? userEpisodeWatchService.listWatchedEpisodeIds(userId, subjectId)
                : Collections.emptySet();
        boolean includeWatched = userId > 0;
        dto.setData(page.getRecords().stream()
                .map(entity -> toEpisode(entity, watchedEpisodeIds, includeWatched))
                .toList());
        return dto;
    }

    private SubjectEpisodesDto.Episode toEpisode(
            BangumiEpisodeEntity entity,
            Set<Long> watchedEpisodeIds,
            boolean includeWatched) {
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
        if (includeWatched) {
            episode.setWatched(watchedEpisodeIds.contains(entity.getId().longValue()));
        }
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

        BangumiRating rating = new BangumiRating();
        rating.setRank(row.getRank());
        rating.setScore(row.getScore());
        rating.setCount(parseScoreDetails(row.getScoreDetails()));
        rating.setTotal(rating.getCount().stream().mapToInt(Integer::intValue).sum());
        subject.setRating(rating);

        subject.setLocked(false);
        subject.setImages(imageBackfillService.resolve(row.getImages(), row.getId(), bangumiAccessToken));

        item.setSubject(subject);
        return item;
    }

    private List<Integer> parseScoreDetails(String json) {
        List<Integer> counts = new ArrayList<>(Collections.nCopies(10, 0));
        if (!StringUtils.hasText(json)) {
            return counts;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            for (int i = 1; i <= 10; i++) {
                JsonNode value = node.get(String.valueOf(i));
                if (value != null && value.isNumber()) {
                    counts.set(i - 1, value.intValue());
                }
            }
        } catch (JsonProcessingException ignored) {
            return counts;
        }
        return counts;
    }
}
