package com.ligg.common.thirdparty.bangumi.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ligg.common.model.ImageFour;
import lombok.Data;

import java.util.List;

/**
 * Bangumi 条目角色 {@code GET /p1/subjects/{id}/characters} 响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubjectCharactersDto {

    private List<Item> data;
    private Integer total;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private Character character;
        private List<Cast> casts;
        private Integer type;
        private Integer order;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Character {
        private Integer id;
        private String name;
        private String nameCN;
        private Integer role;
        private String info;
        private Integer comment;
        private Boolean lock;
        private Boolean nsfw;
        private ImageFour images;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cast {
        private Person person;
        private Integer relation;
        private String summary;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Person {
        private Integer id;
        private String name;
        private String nameCN;
        private Integer type;
        private String info;
        private List<String> career;
        private Integer comment;
        private Boolean lock;
        private Boolean nsfw;
        private ImageFour images;
    }
}
