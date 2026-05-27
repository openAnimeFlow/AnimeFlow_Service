package com.ligg.common.thirdparty;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ligg.common.model.CoverImages;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Bangumi 条目详情 {@code GET /p1/subjects/{id}} 响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubjectDetailDto {

    private Integer id;
    private String name;
    private String nameCN;
    private Integer type;
    private String info;
    private String summary;
    private Integer eps;
    private Integer volumes;
    private Boolean locked;
    private Boolean nsfw;
    private Boolean series;
    private Integer redirect;
    private Integer seriesEntry;
    private Airtime airtime;
    private Map<String, Integer> collection;
    private List<InfoboxEntry> infobox;
    private List<String> metaTags;
    private Platform platform;
    private BangumiRating rating;
    private List<SubjectTag> tags;
    private CoverImages images;

    /** 仅在上游请求携带有效 Bearer 时返回 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private SubjectInterest interest;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Airtime {
        private String date;
        private Integer month;
        private Integer weekday;
        private Integer year;
    }

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
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Platform {
        private Integer id;
        private String type;
        private String typeCN;
        private String alias;
        private Integer order;
        private Boolean enableHeader;
        private String wikiTpl;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubjectTag {
        private String name;
        private Integer count;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubjectInterest {
        private Long id;
        private Integer rate;
        private Integer type;
        private String comment;
        private List<String> tags;
        private Integer epStatus;
        private Integer volStatus;
        @com.fasterxml.jackson.annotation.JsonProperty("private")
        private Boolean privately;
        private Long updatedAt;
    }
}
