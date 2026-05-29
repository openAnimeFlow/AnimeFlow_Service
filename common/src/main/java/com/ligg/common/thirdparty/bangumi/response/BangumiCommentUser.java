package com.ligg.common.thirdparty.bangumi.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Bangumi 评论中的用户信息（章节评论、条目评论等共用）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BangumiCommentUser {

    private Integer id;
    private String username;
    private String nickname;
    private BangumiUserAvatar avatar;
    private Integer group;
    private String sign;
    private Long joinedAt;
}
