package com.ligg.common.thirdparty.bangumi.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Bangumi 条目评论 {@code GET /p1/subjects/{id}/comments} 响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubjectCommentsDto {

    private List<Comment> data;
    private Integer total;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Comment {
        private Long id;
        private BangumiCommentUser user;
        private Integer type;
        private Integer rate;
        private String comment;
        private Long updatedAt;
        private List<EpisodeCommentDto.Reaction> reactions;
    }
}
