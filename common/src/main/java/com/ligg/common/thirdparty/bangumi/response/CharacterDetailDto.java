package com.ligg.common.thirdparty.bangumi.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ligg.common.model.ImageFour;
import lombok.Data;

import java.util.List;

/**
 * Bangumi 角色详情 {@code GET /p1/characters/{id}} 响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CharacterDetailDto {

    private Integer id;
    private String name;
    private String nameCN;
    private Integer role;
    private List<InfoboxEntry> infobox;
    private String info;
    private String summary;
    private Integer comment;
    private Integer collects;
    private Boolean lock;
    private Integer redirect;
    private Boolean nsfw;
    private ImageFour images;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InfoboxEntry {
        private String key;
        private List<InfoboxValue> values;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InfoboxValue {
        private String v;
        private String k;
    }
}
