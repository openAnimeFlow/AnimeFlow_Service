package com.ligg.accesslog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.constants.Constants;
import com.ligg.common.entity.ApiAccessLogEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/** 异步写入访问日志 Redis 队列。 */
@Slf4j
@Service
public class ApiAccessLogRedisProducer {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Executor accessLogRedisExecutor;

    public ApiAccessLogRedisProducer(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            @Qualifier("accessLogRedisExecutor") Executor accessLogRedisExecutor
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.accessLogRedisExecutor = accessLogRedisExecutor;
    }

    public void enqueue(ApiAccessLogEntity entity) {
        try {
            accessLogRedisExecutor.execute(() -> push(entity));
        } catch (TaskRejectedException exception) {
            log.warn("访问日志 Redis 写入队列已满，丢弃日志，traceId={}", entity.getTraceId());
        }
    }

    private void push(ApiAccessLogEntity entity) {
        try {
            String payload = objectMapper.writeValueAsString(entity);
            stringRedisTemplate.opsForList().rightPush(Constants.API_ACCESS_LOG_QUEUE_KEY, payload);
        } catch (JsonProcessingException exception) {
            log.error("访问日志序列化失败，traceId={}", entity.getTraceId(), exception);
        } catch (Exception exception) {
            log.error("访问日志写入 Redis 失败，traceId={}", entity.getTraceId(), exception);
        }
    }
}
