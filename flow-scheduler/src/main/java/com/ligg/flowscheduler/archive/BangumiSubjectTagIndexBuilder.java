package com.ligg.flowscheduler.archive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.entity.BangumiSubjectEntity;
import com.ligg.common.entity.BangumiSubjectTagEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从归档 JSON 中构建用于推荐的标签关系索引。
 */
@Component
@RequiredArgsConstructor
public class BangumiSubjectTagIndexBuilder {

    private static final int MAX_TAG_NAME_LENGTH = 255;

    private final ObjectMapper objectMapper;

    public List<BangumiSubjectTagEntity> buildBatch(List<BangumiSubjectEntity> subjects) {
        List<BangumiSubjectTagEntity> rows = new ArrayList<>();
        for (BangumiSubjectEntity subject : subjects) {
            rows.addAll(build(subject));
        }
        return rows;
    }

    private List<BangumiSubjectTagEntity> build(BangumiSubjectEntity subject) {
        List<BangumiSubjectTagEntity> rows = new ArrayList<>();
        addRows(rows, subject.getId(), BangumiSubjectTagEntity.TYPE_TAG,
                extractNames(subject.getTags(), true));
        addRows(rows, subject.getId(), BangumiSubjectTagEntity.TYPE_META_TAG,
                extractNames(subject.getMetaTags(), false));
        return rows;
    }

    private List<String> extractNames(String json, boolean tagObject) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                throw new IllegalArgumentException("标签 JSON 必须为数组");
            }
            Set<String> names = new LinkedHashSet<>();
            for (JsonNode item : root) {
                String value = tagObject ? item.path("name").asText() : item.asText();
                if (StringUtils.hasText(value)) {
                    String name = value.trim();
                    if (name.length() > MAX_TAG_NAME_LENGTH) {
                        throw new IllegalArgumentException("标签名长度超过 " + MAX_TAG_NAME_LENGTH);
                    }
                    names.add(name);
                }
            }
            return new ArrayList<>(names);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法构建条目标签索引", e);
        }
    }

    private static void addRows(List<BangumiSubjectTagEntity> rows, Integer subjectId, int tagType, List<String> names) {
        for (String name : names) {
            BangumiSubjectTagEntity row = new BangumiSubjectTagEntity();
            row.setSubjectId(subjectId);
            row.setTagType(tagType);
            row.setTagName(name);
            rows.add(row);
        }
    }
}
