package com.ligg.common.thirdparty.bangumi.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Bangumi 角色吐槽 {@code GET /p1/characters/{id}/comments} 单条记录。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CharacterCommentDto {

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
    @JsonProperty("relatedPhotoID")
    private Long relatedPhotoId;
    private List<Reply> replies;
    private BangumiCommentUser user;
    private List<EpisodeCommentDto.Reaction> reactions;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Reply {
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
        @JsonProperty("relatedPhotoID")
        private Long relatedPhotoId;
        private BangumiCommentUser user;
    }
}
