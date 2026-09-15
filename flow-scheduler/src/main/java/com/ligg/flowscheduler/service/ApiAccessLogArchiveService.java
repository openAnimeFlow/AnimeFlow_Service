package com.ligg.flowscheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.constants.Constants;
import com.ligg.common.entity.ApiAccessLogEntity;
import com.ligg.flowscheduler.config.ApiAccessLogArchiveProperties;
import com.ligg.flowscheduler.mapper.ApiAccessLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 归档或清理上月接口访问日志。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiAccessLogArchiveService {

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = redisScript(
            "lua/access_log_release_lock.lua");

    private final ApiAccessLogMapper apiAccessLogMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ApiAccessLogArchiveProperties properties;

    public void archivePreviousMonth() {
        if (!properties.isEnabled()) {
            return;
        }
        String lockToken = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                Constants.API_ACCESS_LOG_ARCHIVE_LOCK_KEY,
                lockToken,
                properties.getLockTtlSeconds(),
                TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            log.info("接口访问日志月度归档已由其他实例执行，跳过");
            return;
        }

        try {
            YearMonth month = YearMonth.now().minusMonths(1);
            LocalDateTime from = month.atDay(1).atStartOfDay();
            LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay();
            if (!properties.isPersistFile()) {
                long count = deletePreviousMonthRows(from, to);
                log.info("接口访问日志月度清理完成（未持久化文件）: month={}, rows={}", month, count);
                return;
            }
            Path directory = Path.of(properties.getDirectory());
            Files.createDirectories(directory);
            Path target = directory.resolve("api-access-log-" + month + ".log");
            Path temporary = directory.resolve(target.getFileName() + ".tmp-" + UUID.randomUUID());
            long count = 0;
            List<Long> archivedIds = new ArrayList<>();
            LocalDateTime cursorTime = from;
            long cursorId = 0;

            try {
                try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                    while (true) {
                        List<ApiAccessLogEntity> batch = apiAccessLogMapper.selectArchiveBatch(
                                from, to, cursorTime, cursorId, Math.max(1, properties.getBatchSize()));
                        if (batch == null || batch.isEmpty()) {
                            break;
                        }
                        for (ApiAccessLogEntity entry : batch) {
                            writer.write(objectMapper.writeValueAsString(entry));
                            writer.newLine();
                            archivedIds.add(entry.getId());
                        }
                        ApiAccessLogEntity last = batch.get(batch.size() - 1);
                        cursorTime = last.getRequestTime();
                        cursorId = last.getId();
                        count += batch.size();
                    }
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                deleteArchivedRows(archivedIds);
                log.info("接口访问日志月度归档完成: month={}, rows={}, file={}", month, count, target);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (Exception e) {
            log.error("接口访问日志月度归档失败", e);
        } finally {
            stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT,
                    List.of(Constants.API_ACCESS_LOG_ARCHIVE_LOCK_KEY), lockToken);
        }
    }

    /** 未启用文件归档时，按游标分页删除上月数据，避免一次性加载全部记录。 */
    private long deletePreviousMonthRows(LocalDateTime from, LocalDateTime to) {
        long count = 0;
        LocalDateTime cursorTime = from;
        long cursorId = 0;
        while (true) {
            List<ApiAccessLogEntity> batch = apiAccessLogMapper.selectArchiveBatch(
                    from, to, cursorTime, cursorId, Math.max(1, properties.getBatchSize()));
            if (batch == null || batch.isEmpty()) {
                return count;
            }
            List<Long> ids = batch.stream().map(ApiAccessLogEntity::getId).toList();
            deleteArchivedRows(ids);
            ApiAccessLogEntity last = batch.get(batch.size() - 1);
            cursorTime = last.getRequestTime();
            cursorId = last.getId();
            count += batch.size();
        }
    }

    /** 仅删除已经成功写入归档文件的行，避免按日期条件误删并发写入的数据。 */
    private void deleteArchivedRows(List<Long> ids) {
        for (int from = 0; from < ids.size(); from += Math.max(1, properties.getBatchSize())) {
            int to = Math.min(ids.size(), from + Math.max(1, properties.getBatchSize()));
            apiAccessLogMapper.deleteByIds(ids.subList(from, to));
        }
    }

    private static DefaultRedisScript<Long> redisScript(String resourcePath) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(resourcePath));
        script.setResultType(Long.class);
        return script;
    }
}
