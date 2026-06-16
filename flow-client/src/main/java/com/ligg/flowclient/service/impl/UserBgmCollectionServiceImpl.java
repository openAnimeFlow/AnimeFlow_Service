package com.ligg.flowclient.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.entity.BangumiSubjectEntity;
import com.ligg.common.entity.UserBgmCollectionEntity;
import com.ligg.common.model.CoverImages;
import com.ligg.common.thirdparty.bangumi.model.BangumiRating;
import com.ligg.common.thirdparty.bangumi.response.UserCollectionsDto;
import com.ligg.common.utils.Utils;
import com.ligg.common.vo.bangumi.UserCollectionsVo;
import com.ligg.flowclient.mapper.BangumiSubjectMapper;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.mybatis.LimitOffsetPage;
import com.ligg.flowclient.service.JwtTokenService;
import com.ligg.flowclient.service.UserBgmCollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserBgmCollectionServiceImpl implements UserBgmCollectionService {

    private static final TypeReference<List<String>> TAG_LIST_TYPE = new TypeReference<>() {
    };

    private final JwtTokenService jwtTokenService;
    private final UserBgmCollectionMapper userBgmCollectionMapper;
    private final BangumiSubjectMapper bangumiSubjectMapper;
    private final ObjectMapper objectMapper;

    @Override
    public UserCollectionsVo listMyCollections(String accessToken, int subjectType, int type, int limit, int offset) {
        Long userId = jwtTokenService.validateAccessToken(accessToken);

        UserCollectionsVo vo = new UserCollectionsVo();
        if (limit <= 0) {
            vo.setData(Collections.emptyList());
            vo.setTotal(0);
            return vo;
        }

        LambdaQueryWrapper<UserBgmCollectionEntity> wrapper = new LambdaQueryWrapper<UserBgmCollectionEntity>()
                .eq(UserBgmCollectionEntity::getUserId, userId)
                .eq(UserBgmCollectionEntity::getType, type)
                .inSql(UserBgmCollectionEntity::getSubjectId,
                        "SELECT id FROM bangumi_subject WHERE type = " + subjectType)
                .orderByDesc(UserBgmCollectionEntity::getBgmUpdatedAt);

        IPage<UserBgmCollectionEntity> page = userBgmCollectionMapper.selectPage(
                new LimitOffsetPage<>(limit, offset), wrapper);

        List<UserBgmCollectionEntity> rows = page.getRecords();
        if (rows.isEmpty()) {
            vo.setData(Collections.emptyList());
            vo.setTotal((int) page.getTotal());
            return vo;
        }

        List<Integer> subjectIds = rows.stream()
                .map(UserBgmCollectionEntity::getSubjectId)
                .distinct()
                .toList();
        Map<Integer, BangumiSubjectEntity> subjectMap = bangumiSubjectMapper.selectBatchIds(subjectIds).stream()
                .collect(Collectors.toMap(BangumiSubjectEntity::getId, Function.identity()));

        List<UserCollectionsDto.Item> items = new ArrayList<>(rows.size());
        for (UserBgmCollectionEntity row : rows) {
            BangumiSubjectEntity subject = subjectMap.get(row.getSubjectId());
            items.add(toItem(row, subject));
        }

        vo.setData(items);
        vo.setTotal((int) page.getTotal());
        return vo;
    }

    private UserCollectionsDto.Item toItem(UserBgmCollectionEntity row, BangumiSubjectEntity subject) {
        UserCollectionsDto.Item item = new UserCollectionsDto.Item();
        item.setId(row.getSubjectId());
        item.setInterest(toInterest(row));

        if (subject != null) {
            item.setName(subject.getName());
            item.setNameCN(subject.getNameCn());
            item.setType(subject.getType());
            item.setNsfw(Boolean.TRUE.equals(subject.getNsfw()));
            item.setRating(toRating(subject));
        } else {
            item.setName("");
            item.setType(subjectTypeFallback(row));
            item.setNsfw(false);
            item.setRating(emptyRating());
        }

        CoverImages images = parseImages(row.getImages());
        Utils.applyWsrvCdnInPlace(images);
        item.setImages(images);
        item.setInfo("");
        item.setLocked(false);
        return item;
    }

    private int subjectTypeFallback(UserBgmCollectionEntity row) {
        return row.getSubjectId() != null ? 2 : 0;
    }

    private UserCollectionsDto.Interest toInterest(UserBgmCollectionEntity row) {
        UserCollectionsDto.Interest interest = new UserCollectionsDto.Interest();
        interest.setId(row.getBgmInterestId());
        interest.setRate(row.getRate() != null ? row.getRate() : 0);
        interest.setType(row.getType());
        interest.setComment(row.getComment() != null ? row.getComment() : "");
        interest.setTags(parseTags(row.getTags()));
        interest.setEpStatus(row.getEpStatus() != null ? row.getEpStatus() : 0);
        interest.setVolStatus(row.getVolStatus() != null ? row.getVolStatus() : 0);
        interest.setPrivate_(Boolean.TRUE.equals(row.getIsPrivate()));
        interest.setUpdatedAt(row.getBgmUpdatedAt() != null ? row.getBgmUpdatedAt() : 0L);
        return interest;
    }

    private BangumiRating toRating(BangumiSubjectEntity subject) {
        BangumiRating rating = new BangumiRating();
        rating.setRank(subject.getRank());
        rating.setScore(subject.getScore());
        List<Integer> counts = parseScoreDetails(subject.getScoreDetails());
        rating.setCount(counts);
        rating.setTotal(counts.stream().mapToInt(Integer::intValue).sum());
        return rating;
    }

    private BangumiRating emptyRating() {
        BangumiRating rating = new BangumiRating();
        rating.setRank(0);
        rating.setScore(0.0);
        rating.setCount(Collections.nCopies(10, 0));
        rating.setTotal(0);
        return rating;
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

    private CoverImages parseImages(String json) {
        if (!StringUtils.hasText(json)) {
            return new CoverImages();
        }
        try {
            return objectMapper.readValue(json, CoverImages.class);
        } catch (JsonProcessingException e) {
            return new CoverImages();
        }
    }

    private List<String> parseTags(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<String> tags = objectMapper.readValue(json, TAG_LIST_TYPE);
            return tags != null ? tags : Collections.emptyList();
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
