package com.ligg.flowscheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.ligg.common.constants.Constants;
import com.ligg.common.entity.ApiAccessLogEntity;
import com.ligg.flowscheduler.mapper.ApiAccessLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 从 Redis 队列批量同步访问日志到 MySQL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiAccessLogSyncService {

    private static final int BATCH_SIZE = 500;
    private static final int MAX_BATCHES_PER_RUN = 20;
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = redisScript("lua/access_log_release_lock.lua");
    private static final DefaultRedisScript<Long> MOVE_TO_DEAD_LETTER_SCRIPT = redisScript(
            "lua/access_log_move_to_dead_letter.lua");

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ApiAccessLogMapper apiAccessLogMapper;

    public void drainQueue() {
        String lockToken = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                Constants.API_ACCESS_LOG_SYNC_LOCK_KEY, lockToken, LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            log.debug("接口访问日志同步任务已在其他实例执行，跳过本次调度");
            return;
        }

        try {
            for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
                if (!drainBatch()) {
                    return;
                }
            }
            log.info("接口访问日志单次同步已达到批次上限，等待下一次调度继续处理");
        } finally {
            stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT,
                    Collections.singletonList(Constants.API_ACCESS_LOG_SYNC_LOCK_KEY), lockToken);
        }
    }

    private boolean drainBatch() {
        List<String> payloads = stringRedisTemplate.opsForList()
                .range(Constants.API_ACCESS_LOG_QUEUE_KEY, 0, BATCH_SIZE - 1);
        if (payloads == null || payloads.isEmpty()) {
            return false;
        }

        List<ApiAccessLogEntity> logs = new ArrayList<>(payloads.size());
        for (String payload : payloads) {
            try {
                logs.add(objectMapper.readValue(payload, ApiAccessLogEntity.class));
            } catch (JsonProcessingException exception) {
                moveToDeadLetter(payload, exception);
                return true;
            }
        }
        try {
            apiAccessLogMapper.insertBatch(logs);
            stringRedisTemplate.opsForList().trim(
                    Constants.API_ACCESS_LOG_QUEUE_KEY, payloads.size(), -1);
            return true;
        } catch (Exception exception) {
            log.error("访问日志批量写入 MySQL 失败，保留 Redis 队列等待重试，size={}",
                    logs.size(), exception);
            return false;
        }
    }

    private void moveToDeadLetter(String invalidPayload, JsonProcessingException exception) {
        Long moved = stringRedisTemplate.execute(MOVE_TO_DEAD_LETTER_SCRIPT,
                List.of(Constants.API_ACCESS_LOG_QUEUE_KEY, Constants.API_ACCESS_LOG_DEAD_LETTER_QUEUE_KEY),
                invalidPayload);
        log.warn("访问日志消息无法解析，已移入死信队列，moved={}", moved, exception);
    }

    private static DefaultRedisScript<Long> redisScript(String resourcePath) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(resourcePath));
        script.setResultType(Long.class);
        return script;
    }
}
