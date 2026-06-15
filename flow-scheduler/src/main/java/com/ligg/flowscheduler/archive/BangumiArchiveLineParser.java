package com.ligg.flowscheduler.archive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 将 jsonlines 单行 JSON 解析为对应的 Bangumi 实体对象。
 *
 * @author Ligg
 */
@Component
@RequiredArgsConstructor
public class BangumiArchiveLineParser {

    private final ObjectMapper objectMapper;

    public BangumiCharacterEntity parseCharacter(String line) throws Exception {
        JsonNode node = objectMapper.readTree(line);
        BangumiCharacterEntity entity = new BangumiCharacterEntity();
        entity.setId(intValue(node, "id"));
        entity.setRole(intValue(node, "role"));
        entity.setName(textValue(node, "name"));
        entity.setInfobox(textValue(node, "infobox"));
        entity.setSummary(textValue(node, "summary"));
        entity.setComments(intValue(node, "comments", 0));
        entity.setCollects(intValue(node, "collects", 0));
        return entity;
    }

    public BangumiEpisodeEntity parseEpisode(String line) throws Exception {
        JsonNode node = objectMapper.readTree(line);
        BangumiEpisodeEntity entity = new BangumiEpisodeEntity();
        entity.setId(intValue(node, "id"));
        entity.setName(textValue(node, "name"));
        entity.setNameCn(textValue(node, "name_cn"));
        entity.setDescription(textValue(node, "description"));
        entity.setAirdate(textValue(node, "airdate"));
        entity.setDisc(intValue(node, "disc", 0));
        entity.setDuration(textValue(node, "duration"));
        entity.setSubjectId(intValue(node, "subject_id"));
        entity.setSort(intValue(node, "sort", 0));
        entity.setType(intValue(node, "type", 0));
        return entity;
    }

    public BangumiPersonEntity parsePerson(String line) throws Exception {
        JsonNode node = objectMapper.readTree(line);
        BangumiPersonEntity entity = new BangumiPersonEntity();
        entity.setId(intValue(node, "id"));
        entity.setName(textValue(node, "name"));
        entity.setType(intValue(node, "type"));
        entity.setCareer(jsonValue(node, "career"));
        entity.setInfobox(textValue(node, "infobox"));
        entity.setSummary(textValue(node, "summary"));
        entity.setComments(intValue(node, "comments", 0));
        entity.setCollects(intValue(node, "collects", 0));
        return entity;
    }

    public BangumiSubjectEntity parseSubject(String line) throws Exception {
        JsonNode node = objectMapper.readTree(line);
        BangumiSubjectEntity entity = new BangumiSubjectEntity();
        entity.setId(intValue(node, "id"));
        entity.setType(intValue(node, "type"));
        entity.setName(textValue(node, "name"));
        entity.setNameCn(textValue(node, "name_cn"));
        entity.setInfobox(textValue(node, "infobox"));
        entity.setPlatform(intValue(node, "platform", 0));
        entity.setSummary(textValue(node, "summary"));
        entity.setNsfw(booleanValue(node, "nsfw"));
        entity.setTags(jsonValue(node, "tags"));
        entity.setMetaTags(jsonValue(node, "meta_tags"));
        entity.setScore(doubleValue(node, "score"));
        entity.setScoreDetails(jsonValue(node, "score_details"));
        entity.setRank(intNullable(node, "rank"));
        entity.setDate(textValue(node, "date"));
        entity.setFavorite(jsonValue(node, "favorite"));
        entity.setSeries(booleanValue(node, "series"));
        return entity;
    }

    public BangumiPersonCharacterEntity parsePersonCharacter(String line) throws Exception {
        JsonNode node = objectMapper.readTree(line);
        BangumiPersonCharacterEntity entity = new BangumiPersonCharacterEntity();
        entity.setPersonId(intValue(node, "person_id"));
        entity.setSubjectId(intValue(node, "subject_id"));
        entity.setCharacterId(intValue(node, "character_id"));
        entity.setType(intValue(node, "type", 0));
        entity.setSummary(textValue(node, "summary"));
        return entity;
    }

    public BangumiPersonRelationEntity parsePersonRelation(String line) throws Exception {
        JsonNode node = objectMapper.readTree(line);
        BangumiPersonRelationEntity entity = new BangumiPersonRelationEntity();
        entity.setPersonType(textValue(node, "person_type"));
        entity.setPersonId(intValue(node, "person_id"));
        entity.setRelatedPersonId(intValue(node, "related_person_id"));
        entity.setRelationType(intValue(node, "relation_type"));
        entity.setSpoiler(booleanValue(node, "spoiler"));
        entity.setEnded(booleanValue(node, "ended"));
        return entity;
    }

    public BangumiSubjectCharacterEntity parseSubjectCharacter(String line) throws Exception {
        JsonNode node = objectMapper.readTree(line);
        BangumiSubjectCharacterEntity entity = new BangumiSubjectCharacterEntity();
        entity.setCharacterId(intValue(node, "character_id"));
        entity.setSubjectId(intValue(node, "subject_id"));
        entity.setType(intValue(node, "type"));
        entity.setOrder(intValue(node, "order", 0));
        return entity;
    }

    public BangumiSubjectPersonEntity parseSubjectPerson(String line) throws Exception {
        JsonNode node = objectMapper.readTree(line);
        BangumiSubjectPersonEntity entity = new BangumiSubjectPersonEntity();
        entity.setPersonId(intValue(node, "person_id"));
        entity.setSubjectId(intValue(node, "subject_id"));
        entity.setPosition(intValue(node, "position"));
        entity.setAppearEps(textValue(node, "appear_eps"));
        return entity;
    }

    public BangumiSubjectRelationEntity parseSubjectRelation(String line) throws Exception {
        JsonNode node = objectMapper.readTree(line);
        BangumiSubjectRelationEntity entity = new BangumiSubjectRelationEntity();
        entity.setSubjectId(intValue(node, "subject_id"));
        entity.setRelationType(intValue(node, "relation_type"));
        entity.setRelatedSubjectId(intValue(node, "related_subject_id"));
        entity.setOrder(intValue(node, "order", 0));
        return entity;
    }

    private static String textValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("");
    }

    private static String jsonValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.toString();
    }

    private static int intValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        return value.asInt();
    }

    private static int intValue(JsonNode node, String field, int defaultValue) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        return value.asInt(defaultValue);
    }

    private static Integer intNullable(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asInt();
    }

    private static Double doubleValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asDouble();
    }

    private static Boolean booleanValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return false;
        }
        return value.asBoolean(false);
    }
}
