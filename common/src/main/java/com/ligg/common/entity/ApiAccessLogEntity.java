package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口访问日志实体。
 */
@Data
@TableName("api_access_log")
public class ApiAccessLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件唯一标识，用于 Redis 重试后的幂等写入。 */
    private String eventId;
    private String traceId;
    private LocalDateTime requestTime;
    private String method;
    private String uri;
    private String route;
    private String queryString;
    private String clientIp;
    private String userAgent;
    private String referer;
    private Integer httpStatus;
    private Boolean success;
    private Integer costMs;
    private String exceptionType;
    private LocalDateTime createdAt;
}
