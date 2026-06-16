package com.ligg.flowclient.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.model.CoverImages;
import com.ligg.common.thirdparty.bangumi.model.BangumiRating;
import com.ligg.common.thirdparty.bangumi.response.UserCollectionsDto;
import com.ligg.common.utils.Utils;
import com.ligg.common.vo.bangumi.UserCollectionsVo;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.module.dto.UserBgmCollectionRow;
import com.ligg.flowclient.service.JwtTokenService;
import com.ligg.flowclient.service.UserBgmCollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserBgmCollectionServiceImpl implements UserBgmCollectionService {

    private static final TypeReference<List<String>> TAG_LIST_TYPE = new TypeReference<>() {
    };

    private final JwtTokenService jwtTokenService;
    private final UserBgmCollectionMapper userBgmCollectionMapper;
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

        long total = userBgmCollectionMapper.countByUserFilter(userId, type, subjectType);
        vo.setTotal((int) total);
        if (total == 0) {
            vo.setData(Collections.emptyList());
            return vo;
        }

        List<UserBgmCollectionRow> rows = userBgmCollectionMapper.selectPageByUserFilter(
                userId, type, subjectType, limit, offset);

        List<UserCollectionsDto.Item> items = new ArrayList<>(rows.size());
        for (UserBgmCollectionRow row : rows) {
            items.add(toItem(row));
        }

        vo.setData(items);
        return vo;
    }

    private UserCollectionsDto.Item toItem(UserBgmCollectionRow row) {
        UserCollectionsDto.Item item = new UserCollectionsDto.Item();
        item.setId(row.getSubjectId());
        item.setInterest(toInterest(row));

        if (StringUtils.hasText(row.getSubjectName())) {
            item.setName(row.getSubjectName());
            item.setNameCN(row.getSubjectNameCn());
            item.setType(row.getSubjectType());
            item.setNsfw(Boolean.TRUE.equals(row.getNsfw()));
            item.setRating(toRating(row));
        } else {
            item.setName("");
            item.setType(row.getSubjectType() != null ? row.getSubjectType() : 2);
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

    private UserCollectionsDto.Interest toInterest(UserBgmCollectionRow row) {
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

    private BangumiRating toRating(UserBgmCollectionRow row) {
        BangumiRating rating = new BangumiRating();
        rating.setRank(row.getRank());
        rating.setScore(row.getScore());
        List<Integer> counts = parseScoreDetails(row.getScoreDetails());
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
