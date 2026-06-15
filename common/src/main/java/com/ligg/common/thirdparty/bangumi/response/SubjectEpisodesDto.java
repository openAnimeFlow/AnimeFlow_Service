package com.ligg.common.thirdparty.bangumi.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Bangumi 条目章节
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubjectEpisodesDto {

    private List<Episode> data;
    private Integer total;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Episode {
        private Long id;
        @JsonProperty("subjectID")
        private Integer subjectId;
        private Integer sort;
        private Integer type;
        private Integer disc;
        private String name;
        private String nameCN;
        private String duration;
        private String airdate;
        private Integer comment;
        private String desc;
    }
}
