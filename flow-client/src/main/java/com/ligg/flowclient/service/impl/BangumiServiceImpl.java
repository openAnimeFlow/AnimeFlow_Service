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
import com.ligg.common.constants.Constants;
import com.ligg.common.entity.BangumiEpisodeEntity;
import com.ligg.common.entity.BangumiSubjectEntity;
import com.ligg.common.model.CoverImages;
import com.ligg.common.thirdparty.bangumi.model.BangumiRating;
import com.ligg.common.thirdparty.bangumi.model.BangumiSubject;
import com.ligg.common.thirdparty.bangumi.request.SearchSubjectsBody;
import com.ligg.common.thirdparty.bangumi.request.SearchSubjectsFilter;
import com.ligg.common.thirdparty.bangumi.response.SubjectDetailDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectEpisodesDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectRelationsDto.RelationInfo;
import com.ligg.common.thirdparty.bangumi.response.SubjectsDto;
import com.ligg.common.utils.InfoboxParser;
import com.ligg.common.utils.Utils;
import com.ligg.common.vo.bangumi.SearchSuggestionsVo;
import com.ligg.common.vo.bangumi.SubjectDetailVo;
import com.ligg.common.vo.bangumi.SubjectRelationsVo;
import com.ligg.common.vo.bangumi.SubjectsVo;
import com.ligg.flowclient.mapper.BangumiEpisodeMapper;
import com.ligg.flowclient.mapper.BangumiSubjectMapper;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.module.dto.SearchSuggestionRow;
import com.ligg.flowclient.module.dto.SubjectRecommendationRow;
import com.ligg.flowclient.module.dto.SubjectRelationRow;
import com.ligg.flowclient.module.dto.SubjectSearchRow;
import com.ligg.flowclient.module.dto.UserSubjectInterestRow;
import com.ligg.flowclient.mybatis.LimitOffsetPage;
import com.ligg.flowclient.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class BangumiServiceImpl implements BangumiService {

    private static final Pattern SEASON_TITLE_PATTERN = Pattern.compile(
            "(第[0-9一二三四五六七八九十百]+[季期]|season\\s*[0-9]+|s[0-9]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPACT_SEASON_QUALIFIER_PATTERN = Pattern.compile(
            "(第[0-9一二三四五六七八九十百]+[季期]|season[0-9]+|s[0-9]+)",
            Pattern.CASE_INSENSITIVE);

    private final BangumiEpisodeMapper episodeMapper;
    private final BangumiSubjectMapper subjectMapper;
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
        SubjectsDto dto = new SubjectsDto();
        if (body == null || !StringUtils.hasText(body.getKeyword()) || limit <= 0) {
            dto.setData(Collections.emptyList());
            dto.setTotal(0);
            return dto;
        }

        String keyword = body.getKeyword().trim();
        String compactKeyword = compactSearchKeyword(keyword);
        String anchorKeyword = extractAnchorKeyword(keyword, compactKeyword);
        boolean anchoredTitleSearch = StringUtils.hasText(anchorKeyword);
        LocalSearchCriteria criteria = toLocalSearchCriteria(body);
        int normalizedLimit = Math.min(limit, 100);
        int normalizedOffset = Math.max(offset, 0);

        Integer total = subjectMapper.countLocalSearchSubjects(
                keyword,
                compactKeyword,
                anchorKeyword,
                anchoredTitleSearch,
                criteria.exactSubjectId(),
                criteria.types(),
                criteria.includeNsfw(),
                criteria.tags(),
                criteria.metaTags(),
                criteria.minYear(),
                criteria.maxYear(),
                criteria.minRating(),
                criteria.maxRating(),
                criteria.minRank(),
                criteria.maxRank());
        if (total == null || total == 0) {
            dto.setData(Collections.emptyList());
            dto.setTotal(0);
            return dto;
        }

        List<SubjectSearchRow> rows = subjectMapper.selectLocalSearchSubjects(
                keyword,
                compactKeyword,
                anchorKeyword,
                anchoredTitleSearch,
                criteria.exactSubjectId(),
                criteria.types(),
                criteria.includeNsfw(),
                criteria.tags(),
                criteria.metaTags(),
                criteria.minYear(),
                criteria.maxYear(),
                criteria.minRating(),
                criteria.maxRating(),
                criteria.minRank(),
                criteria.maxRank(),
                criteria.sort(),
                normalizedLimit,
                normalizedOffset);
        dto.setData(rows == null ? Collections.emptyList() : rows.stream().map(this::toSearchSubject).toList());
        dto.setTotal(total);
        return dto;
    }

    private static String compactSearchKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return "";
        }
        return keyword.trim().replaceAll("[\\s　·・:：\\-－—_]+", "");
    }

    private static String extractAnchorKeyword(String keyword, String compactKeyword) {
        if (compactKeyword.length() < 4 || !SEASON_TITLE_PATTERN.matcher(keyword).find()) {
            return "";
        }
        String anchorKeyword = COMPACT_SEASON_QUALIFIER_PATTERN.matcher(compactKeyword).replaceAll("");
        return anchorKeyword.length() >= 2 ? anchorKeyword : "";
    }

    private LocalSearchCriteria toLocalSearchCriteria(SearchSubjectsBody body) {
        SearchSubjectsFilter filter = body.getFilter();
        List<Integer> types = filter != null && filter.getType() != null && !filter.getType().isEmpty()
                ? filter.getType()
                : List.of(2);
        List<String> tags = normalizeTextList(filter != null ? filter.getTags() : null);
        List<String> metaTags = normalizeTextList(filter != null ? filter.getMetaTags() : null);
        boolean includeNsfw = filter != null && Boolean.TRUE.equals(filter.getNsfw());
        Integer exactSubjectId = parseExactSubjectId(body.getKeyword());

        IntRange yearRange = parseYearRange(filter != null ? filter.getDate() : null);
        DoubleRange ratingRange = parseRatingRange(filter != null ? filter.getRating() : null);
        IntRange rankRange = parseRankRange(filter != null ? filter.getRank() : null);
        String sort = body.getSort() != null ? body.getSort().getValue() : "heat";

        return new LocalSearchCriteria(
                types,
                includeNsfw,
                tags,
                metaTags,
                yearRange.min(),
                yearRange.max(),
                ratingRange.min(),
                ratingRange.max(),
                rankRange.min(),
                rankRange.max(),
                exactSubjectId,
                sort);
    }

    private BangumiSubject toSearchSubject(SubjectSearchRow row) {
        BangumiSubject subject = new BangumiSubject();
        subject.setId(row.getId());
        subject.setName(row.getName());
        subject.setNameCN(row.getNameCn());
        subject.setType(row.getType());
        subject.setNsfw(row.getNsfw());
        subject.setInfo(InfoboxParser.toInfo(row.getInfobox()));
        subject.setLocked(false);

        BangumiRating rating = new BangumiRating();
        rating.setRank(row.getRank());
        rating.setScore(row.getScore());
        rating.setCount(parseScoreDetails(row.getScoreDetails()));
        rating.setTotal(rating.getCount().stream().mapToInt(Integer::intValue).sum());
        subject.setRating(rating);

        CoverImages images = imageBackfillService.resolve(row.getImages(), row.getId(), null);
        Utils.applyWsrvCdnInPlace(images);
        subject.setImages(images);
        return subject;
    }

    private static List<String> normalizeTextList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static Integer parseExactSubjectId(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        if (!trimmed.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static IntRange parseYearRange(List<String> values) {
        List<Integer> years = extractIntegers(values).stream()
                .filter(value -> value >= 1900 && value <= 2100)
                .toList();
        if (years.isEmpty()) {
            return IntRange.empty();
        }
        if (years.size() == 1) {
            return new IntRange(years.get(0), years.get(0));
        }
        return new IntRange(Collections.min(years), Collections.max(years));
    }

    private static DoubleRange parseRatingRange(List<String> values) {
        List<Double> ratings = extractDoubles(values).stream()
                .filter(value -> value >= 0 && value <= 10)
                .toList();
        if (ratings.isEmpty()) {
            return DoubleRange.empty();
        }
        if (ratings.size() == 1) {
            return new DoubleRange(ratings.get(0), null);
        }
        return new DoubleRange(Collections.min(ratings), Collections.max(ratings));
    }

    private static IntRange parseRankRange(List<String> values) {
        List<Integer> ranks = extractIntegers(values).stream()
                .filter(value -> value > 0)
                .toList();
        if (ranks.isEmpty()) {
            return IntRange.empty();
        }
        if (ranks.size() == 1) {
            return new IntRange(null, ranks.get(0));
        }
        return new IntRange(Collections.min(ranks), Collections.max(ranks));
    }

    private static List<Integer> extractIntegers(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
           Matcher matcher = Pattern.compile("\\d+").matcher(value);
            while (matcher.find()) {
                try {
                    result.add(Integer.parseInt(matcher.group()));
                } catch (NumberFormatException ignored) {
                    // skip oversized values
                }
            }
        }
        return result;
    }

    private static List<Double> extractDoubles(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<Double> result = new ArrayList<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
           Matcher matcher = Pattern.compile("\\d+(?:\\.\\d+)?").matcher(value);
            while (matcher.find()) {
                try {
                    result.add(Double.parseDouble(matcher.group()));
                } catch (NumberFormatException ignored) {
                    // skip oversized values
                }
            }
        }
        return result;
    }

    private record LocalSearchCriteria(
            List<Integer> types,
            boolean includeNsfw,
            List<String> tags,
            List<String> metaTags,
            Integer minYear,
            Integer maxYear,
            Double minRating,
            Double maxRating,
            Integer minRank,
            Integer maxRank,
            Integer exactSubjectId,
            String sort) {
    }

    private record IntRange(Integer min, Integer max) {
        static IntRange empty() {
            return new IntRange(null, null);
        }
    }

    private record DoubleRange(Double min, Double max) {
        static DoubleRange empty() {
            return new DoubleRange(null, null);
        }
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
     * 获取相似条目推荐。
     * <p>
     * 推荐逻辑分为两段：先读取目标番剧，解析出 type、nsfw、tags、meta_tags 和发行年份；
     * 再把这些轻量参数传给 SQL。SQL 只在同类型、同 NSFW 范围内选择候选番剧，
     * 按标签命中数、公共标签命中数、年份接近度、评分和排名综合排序后分页返回。
     * 这样避免在数据库里反复展开目标番剧的 JSON 标签；分页多取一条判断是否还有下一页，
     * 避免额外执行一次昂贵的 count 查询。
     */
    @Override
    public SubjectsVo getRecommendedSubjects(Integer subjectId, int limit, int offset) {
        SubjectsVo vo = new SubjectsVo();
        if (subjectId == null || limit <= 0) {
            vo.setData(Collections.emptyList());
            vo.setTotal(0);
            return vo;
        }

        int normalizedLimit = Math.min(limit, 50);
        int normalizedOffset = Math.max(offset, 0);
        BangumiSubjectEntity target = subjectMapper.selectById(subjectId);
        if (target == null) {
            vo.setData(Collections.emptyList());
            vo.setTotal(0);
            return vo;
        }

        List<String> tagNames = parseSubjectTagNames(target.getTags());
        List<String> metaTagNames = parseMetaTagNames(target.getMetaTags());
        Integer releaseYear = parseReleaseYear(target.getDate());
        List<SubjectRecommendationRow> rows = subjectMapper.selectRecommendedSubjects(
                subjectId,
                target.getType(),
                target.getNsfw(),
                releaseYear,
                tagNames,
                metaTagNames,
                normalizedLimit + 1,
                normalizedOffset);

        if (rows == null || rows.isEmpty()) {
            vo.setData(Collections.emptyList());
            vo.setTotal(normalizedOffset);
            return vo;
        }

        boolean hasMore = rows.size() > normalizedLimit;
        List<SubjectRecommendationRow> pageRows = hasMore ? rows.subList(0, normalizedLimit) : rows;
        vo.setData(pageRows.stream().map(this::toRecommendedSubject).toList());
        vo.setTotal(normalizedOffset + pageRows.size() + (hasMore ? 1 : 0));
        return vo;
    }

    private List<String> parseSubjectTagNames(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<SubjectDetailDto.SubjectTag> tags = objectMapper.readValue(json, new TypeReference<>() {
            });
            if (tags == null || tags.isEmpty()) {
                return Collections.emptyList();
            }
            return tags.stream()
                    .map(SubjectDetailDto.SubjectTag::getName)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        } catch (JsonProcessingException e) {
            log.warn("解析推荐目标 tags JSON 失败: {}", json, e);
            return Collections.emptyList();
        }
    }

    private List<String> parseMetaTagNames(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<String> tags = objectMapper.readValue(json, new TypeReference<>() {
            });
            if (tags == null || tags.isEmpty()) {
                return Collections.emptyList();
            }
            return tags.stream()
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        } catch (JsonProcessingException e) {
            log.warn("解析推荐目标 meta_tags JSON 失败: {}", json, e);
            return Collections.emptyList();
        }
    }

    private Integer parseReleaseYear(String date) {
        if (!StringUtils.hasText(date) || date.length() < 4) {
            return null;
        }
        String year = date.substring(0, 4);
        if (!year.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return Integer.parseInt(year);
    }

    private BangumiSubject toRecommendedSubject(SubjectRecommendationRow row) {
        BangumiSubject subject = new BangumiSubject();
        subject.setId(row.getId());
        subject.setName(row.getName());
        subject.setNameCN(row.getNameCn());
        subject.setType(row.getType());
        subject.setNsfw(row.getNsfw());
        subject.setInfo(InfoboxParser.toInfo(row.getInfobox()));
        subject.setLocked(false);

        BangumiRating rating = new BangumiRating();
        rating.setRank(row.getRank());
        rating.setScore(row.getScore());
        rating.setCount(parseScoreDetails(row.getScoreDetails()));
        rating.setTotal(rating.getCount().stream().mapToInt(Integer::intValue).sum());
        subject.setRating(rating);

        CoverImages images = imageBackfillService.resolve(row.getImages(), row.getId(), null);
        if (images != null) {
            Utils.applyWsrvCdnInPlace(images);
            subject.setImages(images);
        }
        return subject;
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
