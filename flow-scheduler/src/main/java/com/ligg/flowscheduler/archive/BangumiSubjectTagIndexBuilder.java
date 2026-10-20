package com.ligg.flowscheduler.archive;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ligg.common.entity.BangumiSubjectEntity;
import com.ligg.common.entity.BangumiSubjectTagEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 从归档 JSON 中构建用于推荐的标签关系索引。
 */
@Component
@RequiredArgsConstructor
public class BangumiSubjectTagIndexBuilder {

    private static final int MAX_TAG_NAME_LENGTH = 255;

    private final ObjectMapper objectMapper;

    /** 每个 subject 的标签名称只提取一次，同时供标签关系和搜索索引使用。 */
    public SubjectTags extractTags(BangumiSubjectEntity subject) {
        return new SubjectTags(extractNames(subject.getTags(), true),
                extractNames(subject.getMetaTags(), false));
    }

    /** 逐条消费标签实体，避免汇总整批 subject 的所有标签。 */
    public void forEachTag(Integer subjectId, SubjectTags tags, Consumer<BangumiSubjectTagEntity> consumer) {
        emitRows(subjectId, BangumiSubjectTagEntity.TYPE_TAG, tags.tags(), consumer);
        emitRows(subjectId, BangumiSubjectTagEntity.TYPE_META_TAG, tags.metaTags(), consumer);
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
            return List.copyOf(names);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法构建条目标签索引", e);
        }
    }

    private static void emitRows(Integer subjectId, int tagType, List<String> names,
                                 Consumer<BangumiSubjectTagEntity> consumer) {
        for (String name : names) {
            BangumiSubjectTagEntity row = new BangumiSubjectTagEntity();
            row.setSubjectId(subjectId);
            row.setTagType(tagType);
            row.setTagName(name);
            consumer.accept(row);
        }
    }

    public record SubjectTags(List<String> tags, List<String> metaTags) {
    }
}
