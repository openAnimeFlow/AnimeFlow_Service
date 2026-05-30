package com.ligg.common.thirdparty.bangumi.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ligg.common.model.CoverImages;
import com.ligg.common.thirdparty.bangumi.model.BangumiRating;
import lombok.Data;

import java.util.List;

/**
 * Bangumi 用户条目收藏 {@code GET /p1/users/{user}/collections/subjects} 响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserCollectionsDto {

    private List<Item> data;
    private Integer total;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private Integer id;
        private String name;
        private String nameCN;
        private Integer type;
        private String info;
        private BangumiRating rating;
        private Boolean locked;
        private Boolean nsfw;
        private CoverImages images;
        private Interest interest;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Interest {
        private Long id;
        private Integer rate;
        private Integer type;
        private String comment;
        private List<String> tags;
        private Long updatedAt;
    }
}
