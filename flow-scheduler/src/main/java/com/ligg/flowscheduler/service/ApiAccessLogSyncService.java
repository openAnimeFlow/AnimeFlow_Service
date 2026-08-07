package com.ligg.flowscheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.constants.Constants;
import com.ligg.common.entity.ApiAccessLogEntity;
import com.ligg.flowscheduler.mapper.ApiAccessLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 从 Redis 队列批量同步访问日志到 MySQL。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiAccessLogSyncService {

    private static final int BATCH_SIZE = 500;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ApiAccessLogMapper apiAccessLogMapper;

    public void drainQueue() {
        while (true) {
            List<String> payloads = stringRedisTemplate.opsForList()
                    .range(Constants.API_ACCESS_LOG_QUEUE_KEY, 0, BATCH_SIZE - 1);
            if (payloads == null || payloads.isEmpty()) {
                return;
            }

            List<ApiAccessLogEntity> logs = new ArrayList<>(payloads.size());
            try {
                for (String payload : payloads) {
                    logs.add(objectMapper.readValue(payload, ApiAccessLogEntity.class));
                }
                apiAccessLogMapper.insertBatch(logs);
                stringRedisTemplate.opsForList().trim(
                        Constants.API_ACCESS_LOG_QUEUE_KEY, payloads.size(), -1);
            } catch (Exception exception) {
                log.error("访问日志批量写入 MySQL 失败，保留 Redis 队列等待重试，size={}",
                        logs.size(), exception);
                return;
            }
        }
    }
}
