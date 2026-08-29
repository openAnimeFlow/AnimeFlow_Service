package com.ligg.flowscheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 接口访问日志月度归档配置。 */
@Data
@ConfigurationProperties(prefix = "anime-flow.access-log.archive")
public class ApiAccessLogArchiveProperties {

    private boolean enabled = true;

    /** 每月 1 日凌晨 02:00 归档上月日志。 */
    private String cron = "0 0 2 1 * *";

    /** 定时任务使用的时区。 */
    private String zone = "Asia/Shanghai";

    /** 归档文件保存目录。 */
    private String directory = System.getProperty("user.home") + "/anime-flow/api-access-log";

    /** 单次数据库读取行数。 */
    private int batchSize = 1000;

    /** 分布式锁最长持有时间（秒）。 */
    private long lockTtlSeconds = 2 * 3600L;
}
