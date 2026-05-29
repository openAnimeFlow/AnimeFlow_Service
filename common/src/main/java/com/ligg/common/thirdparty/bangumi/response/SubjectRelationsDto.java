package com.ligg.common.thirdparty.bangumi.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ligg.common.thirdparty.bangumi.model.BangumiSubject;
import lombok.Data;

import java.util.List;

/**
 * Bangumi 条目关联 {@code GET /p1/subjects/{id}/relations} 响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubjectRelationsDto {

    private List<Item> data;
    private Integer total;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private BangumiSubject subject;
        private RelationInfo relation;
        private Integer order;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RelationInfo {
        private Integer id;
        private String en;
        private String cn;
        private String jp;
        private String desc;
    }
}
