package com.ligg.flowclient.service.impl;

import com.ligg.flowclient.service.CollectionWriteLock;
import com.ligg.flowclient.service.LocalCollectionWriter;
import com.ligg.flowclient.service.CollectionUploadService;
import com.ligg.flowclient.service.BangumiOAuthTokenService;
import com.ligg.flowclient.module.vo.CollectionUpdateVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.model.CoverImages;
import com.ligg.common.thirdparty.bangumi.model.BangumiRating;
import com.ligg.common.thirdparty.bangumi.response.UserCollectionsDto;
import com.ligg.common.utils.InfoboxParser;
import com.ligg.common.utils.Utils;
import com.ligg.common.vo.bangumi.UserCollectionsVo;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.module.dto.UpdateUserCollectionDto;
import com.ligg.flowclient.module.dto.UserBgmCollectionRow;
import com.ligg.flowclient.service.ImageBackfillService;
import com.ligg.flowclient.service.UserBgmCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserBgmCollectionServiceImpl implements UserBgmCollectionService {

    private static final TypeReference<List<String>> TAG_LIST_TYPE = new TypeReference<>() {
    };

    private final UserBgmCollectionMapper userBgmCollectionMapper;
    private final ObjectMapper objectMapper;
    private final CollectionWriteLock collectionWriteLock;
    private final LocalCollectionWriter localCollectionWriter;
    private final CollectionUploadService collectionUploadService;
    private final BangumiOAuthTokenService bangumiOAuthTokenService;
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
    public CollectionUpdateVo updateCollection(
            Long userId, int subjectId, UpdateUserCollectionDto dto) {
        return collectionWriteLock.execute(userId, subjectId, () -> {
            var oauth = bangumiOAuthTokenService.findBangumiOauth(userId);
            var row = localCollectionWriter.save(userId, subjectId, dto, oauth);
            return collectionUploadService.upload(row);
        });
    }

    private UserCollectionsDto.Item toItem(UserBgmCollectionRow row, String accessToken) {
        UserCollectionsDto.Item item = new UserCollectionsDto.Item();
        CoverImages images = imageBackfillService.resolve(row.getImages(), row.getSubjectId(), accessToken);
        Utils.applyWsrvCdnInPlace(images);

        item.setId(row.getSubjectId());
        item.setInterest(toInterest(row));
        item.setName(StringUtils.hasText(row.getName()) ? row.getName() : "");
        item.setNameCN(StringUtils.hasText(row.getNameCn()) ? row.getNameCn() : "");
        item.setType(row.getSubjectType() != null ? row.getSubjectType() : 2);
        item.setNsfw(Boolean.TRUE.equals(row.getNsfw()));
        item.setRating(toRating(row));
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
        interest.setUpdatedAt(row.getLocalUpdatedAt() != null
                ? row.getLocalUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
                : row.getBgmUpdatedAt() != null ? row.getBgmUpdatedAt() : 0L);
        interest.setRemoteSyncStatus(row.getRemoteSyncStatus());
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
