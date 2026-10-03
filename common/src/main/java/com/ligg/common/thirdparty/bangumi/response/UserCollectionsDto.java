package com.ligg.common.thirdparty.bangumi.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
        /** 条目正片总集数。 */
        private Integer totalEpisodes;
        /** 当前用户观看到第几集。 */
        private Integer watchedEpisode;
        /** 新番的下一集播出日期（yyyy-MM-dd），已完结时为 null。 */
        private String nextEpisodeAirDate;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Interest {
        private Long id;
        private Integer rate;
        private Integer type;
        private String comment;
        private List<String> tags;
        private Integer epStatus;
        private Integer volStatus;
        @JsonProperty("private")
        private Boolean private_;
        private Long updatedAt;

        private String remoteSyncStatus;
    }
}
