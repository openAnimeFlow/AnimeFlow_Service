package com.ligg.flowclient.module.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前客户端 Presence 上下文，写入 Redis 后随单设备 TTL 过期。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresenceContext {
    private String presenceId;
    private String visitorId;
    private Long userId;
    private String dedupeKey;
    private String clientType;
    private String appVersion;
    private String status;
    private Integer subjectId;
    private Integer episodeId;
    private Integer positionSeconds;
    private long lastSeenAt;
}
