package com.ligg.flowclient.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.entity.UserBgmCollectionEntity;
import com.ligg.common.model.CoverImages;
import com.ligg.common.thirdparty.bangumi.model.BangumiRating;
import com.ligg.common.thirdparty.bangumi.request.UpdateCollectionBody;
import com.ligg.common.thirdparty.bangumi.response.SubjectDetailDto;
import com.ligg.common.thirdparty.bangumi.response.UserCollectionsDto;
import com.ligg.common.utils.InfoboxParser;
import com.ligg.common.utils.Utils;
import com.ligg.common.vo.bangumi.UserCollectionsVo;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.module.dto.UpdateUserCollectionDto;
import com.ligg.flowclient.module.dto.UserBgmCollectionRow;
import com.ligg.flowclient.service.BangumiOAuthExecutor;
import com.ligg.flowclient.service.ImageBackfillService;
import com.ligg.flowclient.service.JwtTokenService;
import com.ligg.flowclient.service.UserBgmCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserBgmCollectionServiceImpl implements UserBgmCollectionService {

    private static final TypeReference<List<String>> TAG_LIST_TYPE = new TypeReference<>() {
    };

    private final JwtTokenService jwtTokenService;
    private final UserBgmCollectionMapper userBgmCollectionMapper;
    private final ObjectMapper objectMapper;
    private final BangumiOAuthExecutor bangumiOAuthExecutor;
    private final BangumiClient bangumiClient;
    private final ImageBackfillService imageBackfillService;

    @Override
    public UserCollectionsVo listMyCollections(
            String accessToken,
            long userId,
            int subjectType,
            int type,
            String keyword,
            int limit,
            int offset) {
        UserCollectionsVo vo = new UserCollectionsVo();
        if (limit <= 0) {
            vo.setData(Collections.emptyList());
            vo.setTotal(0);
            return vo;
        }

        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        long total = userBgmCollectionMapper.countByUserFilter(userId, type, subjectType, normalizedKeyword);
        vo.setTotal((int) total);
        if (total == 0) {
            vo.setData(Collections.emptyList());
            return vo;
        }

        List<UserBgmCollectionRow> rows = userBgmCollectionMapper.selectPageByUserFilter(
                userId, type, subjectType, normalizedKeyword, limit, offset);

        List<UserCollectionsDto.Item> items = new ArrayList<>(rows.size());
        for (UserBgmCollectionRow row : rows) {
            items.add(toItem(row, accessToken));
        }

        vo.setData(items);
        return vo;
    }

    @Override
    public void updateCollection(String accessToken, int subjectId, UpdateUserCollectionDto dto) {
        if (!dto.hasUpdateField()) {
            throw new IllegalArgumentException("至少需要更新一个收藏字段");
        }
        Long userId = jwtTokenService.validateAccessToken(accessToken);
        UpdateCollectionBody body = toUpstreamBody(dto);
        SubjectDetailDto detail = bangumiOAuthExecutor.execute(userId, token -> {
            bangumiClient.updateCollection(token, subjectId, body);
            return bangumiClient.getSubject(subjectId, token);
        });
        upsertLocalCollection(userId, subjectId, dto, detail);
    }

    private static UpdateCollectionBody toUpstreamBody(UpdateUserCollectionDto dto) {
        UpdateCollectionBody body = new UpdateCollectionBody();
        body.setType(dto.getType());
        body.setRate(dto.getRate());
        body.setPrivate_(dto.getPrivate_());
        body.setProgress(dto.getProgress());
        body.setComment(dto.getComment());
        body.setTags(dto.getTags());
        return body;
    }

    private void upsertLocalCollection(
            Long userId, int subjectId, UpdateUserCollectionDto dto, SubjectDetailDto detail) {
        UserBgmCollectionEntity existing = userBgmCollectionMapper.selectOne(
                new LambdaQueryWrapper<UserBgmCollectionEntity>()
                        .eq(UserBgmCollectionEntity::getUserId, userId)
                        .eq(UserBgmCollectionEntity::getSubjectId, subjectId));

        UserBgmCollectionEntity row = existing != null ? existing : new UserBgmCollectionEntity();
        row.setUserId(userId);
        row.setSubjectId(subjectId);
        row.setSubjectType(2);
        row.setSyncTime(LocalDateTime.now());

        SubjectDetailDto.SubjectInterest interest = detail != null ? detail.getInterest() : null;
        if (interest != null) {
            applyInterest(row, interest);
        }
        applyDtoFields(row, dto);
        fillImagesIfMissing(row, existing, detail);
        ensureRequiredFields(row);

        if (existing == null) {
            if (row.getBgmInterestId() == null) {
                throw new IllegalStateException("Bangumi 未返回收藏 ID，无法写入收藏");
            }
            if (row.getType() == null) {
                throw new IllegalStateException("缺少收藏类型，无法写入收藏");
            }
            row.setCreateTime(LocalDateTime.now());
            userBgmCollectionMapper.insert(row);
        } else {
            userBgmCollectionMapper.updateById(row);
        }
    }

    private void applyInterest(UserBgmCollectionEntity row, SubjectDetailDto.SubjectInterest interest) {
        if (interest.getId() != null) {
            row.setBgmInterestId(interest.getId());
        }
        if (interest.getRate() != null) {
            row.setRate(interest.getRate());
        }
        if (interest.getType() != null) {
            row.setType(interest.getType());
        }
        if (interest.getComment() != null) {
            row.setComment(interest.getComment());
        }
        if (interest.getTags() != null) {
            row.setTags(toJson(interest.getTags()));
        }
        if (interest.getEpStatus() != null) {
            row.setEpStatus(interest.getEpStatus());
        }
        if (interest.getVolStatus() != null) {
            row.setVolStatus(interest.getVolStatus());
        }
        if (interest.getPrivately() != null) {
            row.setIsPrivate(interest.getPrivately());
        }
        if (interest.getUpdatedAt() != null) {
            row.setBgmUpdatedAt(interest.getUpdatedAt());
        }
    }

    private void fillImagesIfMissing(
            UserBgmCollectionEntity row,
            UserBgmCollectionEntity existing,
            SubjectDetailDto detail) {
        if (existing != null && StringUtils.hasText(existing.getImages())) {
            return;
        }
        CoverImages images = detail != null ? detail.getImages() : null;
        if (images != null) {
            row.setImages(toJson(images));
        }
    }

    private void ensureRequiredFields(UserBgmCollectionEntity row) {
        if (row.getRate() == null) {
            row.setRate(0);
        }
        if (row.getComment() == null) {
            row.setComment("");
        }
        if (row.getEpStatus() == null) {
            row.setEpStatus(0);
        }
        if (row.getVolStatus() == null) {
            row.setVolStatus(0);
        }
        if (row.getIsPrivate() == null) {
            row.setIsPrivate(false);
        }
        if (row.getBgmUpdatedAt() == null) {
            row.setBgmUpdatedAt(System.currentTimeMillis() / 1000);
        }
    }

    private void applyDtoFields(UserBgmCollectionEntity row, UpdateUserCollectionDto dto) {
        if (dto.getType() != null) {
            row.setType(dto.getType());
        }
        if (dto.getRate() != null) {
            row.setRate(dto.getRate());
        }
        if (dto.getPrivate_() != null) {
            row.setIsPrivate(dto.getPrivate_());
        }
        if (dto.getComment() != null) {
            row.setComment(dto.getComment());
        }
        if (dto.getTags() != null) {
            row.setTags(toJson(dto.getTags()));
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 JSON 失败", e);
        }
    }

    private UserCollectionsDto.Item toItem(UserBgmCollectionRow row, String accessToken) {
        UserCollectionsDto.Item item = new UserCollectionsDto.Item();
        item.setId(row.getSubjectId());
        item.setInterest(toInterest(row));

        if (StringUtils.hasText(row.getName())) {
            item.setName(row.getName());
            item.setNameCN(row.getNameCn());
            item.setType(row.getSubjectType());
            item.setNsfw(Boolean.TRUE.equals(row.getNsfw()));
            item.setRating(toRating(row));
        } else {
            item.setName("");
            item.setType(row.getSubjectType() != null ? row.getSubjectType() : 2);
            item.setNsfw(false);
            item.setRating(emptyRating());
        }

        CoverImages images = imageBackfillService.resolve(row.getImages(), row.getSubjectId(), accessToken);
        Utils.applyWsrvCdnInPlace(images);
        item.setImages(images);
        item.setInfo(InfoboxParser.toInfo(row.getInfobox()));
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
