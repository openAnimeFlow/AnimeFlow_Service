package com.ligg.common.thirdparty.bangumi.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ligg.common.model.ImageFour;
import lombok.Data;

import java.util.List;

/**
 * Bangumi 条目制作人员 {@code GET /p1/subjects/{id}/staffs/persons} 响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubjectStaffPersonsDto {

    private List<Item> data;
    private Integer total;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private Staff staff;
        private List<Position> positions;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Staff {
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

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Position {
        private PositionType type;
        private String summary;
        private String appearEps;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PositionType {
        private Integer id;
        private String en;
        private String cn;
        private String jp;
    }
}
