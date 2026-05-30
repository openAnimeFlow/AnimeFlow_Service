package com.ligg.common.thirdparty.bangumi.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Bangumi 用户资料 {@code GET /p1/users/{username}} 响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfileDto {

    private Integer id;
    private String username;
    private String nickname;
    private BangumiUserAvatar avatar;
    private Integer group;
    private Long joinedAt;
    private String sign;
    private String site;
    private String location;
    private String bio;
    private List<Object> networkServices;
    private Homepage homepage;
    private Stats stats;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Homepage {
        private List<String> left;
        private List<String> right;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Stats {
        /** 条目类型 → 收藏状态(1–5) → 数量 */
        private Map<String, Map<String, Integer>> subject;
        private Mono mono;
        private Integer blog;
        private Integer friend;
        private Integer group;
        private Index index;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Mono {
        private Integer character;
        private Integer person;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Index {
        private Integer create;
        private Integer collect;
    }
}
