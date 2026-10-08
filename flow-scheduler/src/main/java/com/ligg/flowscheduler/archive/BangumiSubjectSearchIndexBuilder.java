package com.ligg.flowscheduler.archive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.entity.BangumiSubjectEntity;
import com.ligg.common.entity.BangumiSubjectSearchEntity;
import com.ligg.flowscheduler.archive.BangumiSubjectTagIndexBuilder.SubjectTags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class BangumiSubjectSearchIndexBuilder {

    private static final Pattern ALIAS_BLOCK_PATTERN = Pattern.compile("\\|\\s*别名\\s*=\\s*\\{(?<body>.*?)\\r?\\n\\}", Pattern.DOTALL);
    private static final Pattern BRACKET_ALIAS_PATTERN = Pattern.compile("\\[(?<alias>[^\\[\\]\\r\\n]+)]");
    private static final Pattern SIMPLE_ALIAS_PATTERN = Pattern.compile("\\|\\s*别名\\s*=\\s*(?<alias>[^\\r\\n]+)");
    private static final Pattern YEAR_PREFIX_PATTERN = Pattern.compile("^(\\d{4})");

    private final ObjectMapper objectMapper;

    public BangumiSubjectSearchEntity build(BangumiSubjectEntity subject, SubjectTags tags) {
        BangumiSubjectSearchEntity index = new BangumiSubjectSearchEntity();
        index.setSubjectId(subject.getId());
        index.setType(subject.getType());
        index.setNsfw(Boolean.TRUE.equals(subject.getNsfw()));
        index.setName(valueOrEmpty(subject.getName()));
        index.setNameCn(valueOrEmpty(subject.getNameCn()));
        index.setAliases(join(extractAliases(subject.getInfobox())));
        index.setTagsText(join(tags.tags()));
        index.setMetaTagsText(join(tags.metaTags()));
        index.setYear(extractYear(subject.getDate()));
        index.setPlatform(subject.getPlatform() != null ? subject.getPlatform() : 0);
        index.setScore(subject.getScore());
        index.setSubjectRank(subject.getRank());
        index.setFavoriteDone(extractFavoriteDone(subject.getFavorite()));
        index.setSearchText(joinSearchText(index));
        return index;
    }

    private List<String> extractAliases(String infobox) {
        Set<String> aliases = new LinkedHashSet<>();
        if (!StringUtils.hasText(infobox)) {
            return List.of();
        }

        Matcher blockMatcher = ALIAS_BLOCK_PATTERN.matcher(infobox);
        if (blockMatcher.find()) {
            Matcher aliasMatcher = BRACKET_ALIAS_PATTERN.matcher(blockMatcher.group("body"));
            while (aliasMatcher.find()) {
                addIfText(aliases, aliasMatcher.group("alias"));
            }
        }

        Matcher simpleMatcher = SIMPLE_ALIAS_PATTERN.matcher(infobox);
        if (simpleMatcher.find()) {
            String alias = simpleMatcher.group("alias").trim();
            if (!alias.startsWith("{")) {
                addIfText(aliases, alias);
            }
        }
        return new ArrayList<>(aliases);
    }

    private Integer extractYear(String date) {
        if (!StringUtils.hasText(date)) {
            return null;
        }
        Matcher matcher = YEAR_PREFIX_PATTERN.matcher(date.trim());
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private Integer extractFavoriteDone(String favoriteJson) {
        if (!StringUtils.hasText(favoriteJson)) {
            return 0;
        }
        try {
            JsonNode root = objectMapper.readTree(favoriteJson);
            JsonNode done = root.get("done");
            return done != null ? done.asInt(0) : 0;
        } catch (Exception e) {
            log.warn("Failed to parse subject favorite for search index: {}", e.getMessage());
            return 0;
        }
    }

    private static String joinSearchText(BangumiSubjectSearchEntity index) {
        return join(List.of(
                index.getName(),
                index.getNameCn(),
                index.getAliases(),
                index.getTagsText(),
                index.getMetaTagsText()));
    }

    private static void addIfText(Set<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value.trim());
        }
    }

    private static String join(List<String> values) {
        return String.join(" ", values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList());
    }

    private static String valueOrEmpty(String value) {
        return value != null ? value : "";
    }
}
