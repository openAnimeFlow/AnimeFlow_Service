package com.ligg.common.thirdparty.bangumi.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ligg.common.model.ImageUrls;
import lombok.Data;

import java.util.List;

/**
 * Bangumi 章节评论 {@code GET /p1/episodes/{id}/comments} 单条记录。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EpisodeCommentDto {

    private Long id;
    @JsonProperty("mainID")
    private Long mainId;
    @JsonProperty("creatorID")
    private Long creatorId;
    @JsonProperty("relatedID")
    private Long relatedId;
    private Long createdAt;
    private String content;
    private Integer state;
    private List<EpisodeCommentDto> replies;
    private User user;
    private List<Reaction> reactions;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class User {
        private Integer id;
        private String username;
        private String nickname;
        private Avatar avatar;
        private Integer group;
        private String sign;
        private Long joinedAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Avatar implements ImageUrls {
        private String small;
        private String medium;
        private String large;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Reaction {
        private List<ReactionUser> users;
        private Integer value;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReactionUser {
        private Integer id;
        private String username;
        private String nickname;
    }
}
