package com.ligg.flowscheduler.archive.service;

import com.ligg.common.entity.BangumiEpisodeEntity;
import com.ligg.flowscheduler.archive.ArchiveDataType;
import com.ligg.flowscheduler.archive.BangumiArchiveLineParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 流式读取 jsonlines 文件，按类型行数和估算数据量双重限制批次后写入数据库。
 *
 * @author Ligg
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BangumiArchiveJsonlinesSyncService {

    private final BangumiArchiveLineParser lineParser;
    private final BangumiArchiveUpsertService upsertService;

    public void syncFile(Path file, ArchiveDataType dataType) throws Exception {
        int batchSize = upsertService.batchSize(dataType);
        long maxBatchBytes = upsertService.maxBatchBytes();
        long delayMs = upsertService.batchDelayMs();
        long total;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            total = switch (dataType) {
                case SUBJECT -> readAndUpsert(reader, batchSize, maxBatchBytes, delayMs, lineParser::parseSubject,
                        upsertService::upsertSubjectBatch);
                case CHARACTER -> readAndUpsert(reader, batchSize, maxBatchBytes, delayMs, lineParser::parseCharacter,
                        upsertService::upsertCharacterBatch);
                case PERSON -> readAndUpsert(reader, batchSize, maxBatchBytes, delayMs, lineParser::parsePerson,
                        upsertService::upsertPersonBatch);
                case EPISODE -> readAndUpsertEpisodes(reader, batchSize, maxBatchBytes, delayMs);
                case PERSON_CHARACTER -> readAndUpsert(reader, batchSize, maxBatchBytes, delayMs, lineParser::parsePersonCharacter,
                        upsertService::upsertPersonCharacterBatch);
                case PERSON_RELATION -> readAndUpsert(reader, batchSize, maxBatchBytes, delayMs, lineParser::parsePersonRelation,
                        upsertService::upsertPersonRelationBatch);
                case SUBJECT_CHARACTER -> readAndUpsert(reader, batchSize, maxBatchBytes, delayMs, lineParser::parseSubjectCharacter,
                        upsertService::upsertSubjectCharacterBatch);
                case SUBJECT_PERSON -> readAndUpsert(reader, batchSize, maxBatchBytes, delayMs, lineParser::parseSubjectPerson,
                        upsertService::upsertSubjectPersonBatch);
                case SUBJECT_RELATION -> readAndUpsert(reader, batchSize, maxBatchBytes, delayMs, lineParser::parseSubjectRelation,
                        upsertService::upsertSubjectRelationBatch);
            };
        }

        log.info("Synced {} rows from {} ({})", total, file.getFileName(), dataType.getFileSuffix());
    }

    private long readAndUpsertEpisodes(BufferedReader reader, int batchSize, long maxBatchBytes, long delayMs)
            throws Exception {
        long total = 0;
        long skipped = 0;
        List<BangumiEpisodeEntity> batch = new ArrayList<>(Math.min(batchSize, 1024));
        long batchBytes = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            long lineBytes = estimateLineBytes(line);
            if (!batch.isEmpty() && lineBytes > maxBatchBytes - batchBytes) {
                total += upsertEpisodeBatchSkippingInvalid(batch);
                batch.clear();
                batchBytes = 0;
                pause(delayMs);
            }
            BangumiEpisodeEntity entity;
            try {
                entity = lineParser.parseEpisode(line);
            } catch (Exception e) {
                skipped++;
                log.warn("Skip episode line (parse error): {}", e.getMessage());
                continue;
            }
            if (!lineParser.isStorableEpisode(entity)) {
                skipped++;
                log.warn(
                        "Skip episode id={}: invalid field (sort={}, disc={}, type={})",
                        entity.getId(),
                        entity.getSort(),
                        entity.getDisc(),
                        entity.getType());
                continue;
            }
            batch.add(entity);
            batchBytes += lineBytes;
            if (batch.size() >= batchSize || batchBytes >= maxBatchBytes) {
                total += upsertEpisodeBatchSkippingInvalid(batch);
                batch.clear();
                batchBytes = 0;
                pause(delayMs);
            }
        }
        if (!batch.isEmpty()) {
            total += upsertEpisodeBatchSkippingInvalid(batch);
        }
        if (skipped > 0) {
            log.warn("Skipped {} invalid episode line(s) in file", skipped);
        }
        return total;
    }

  /**
   * 批量写入 episode；若整批失败则逐条写入并跳过仍无法入库的记录。
   */
    private int upsertEpisodeBatchSkippingInvalid(List<BangumiEpisodeEntity> batch) {
        try {
            upsertService.upsertEpisodeBatch(batch);
            return batch.size();
        } catch (DataIntegrityViolationException batchError) {
            log.warn(
                    "Episode batch upsert failed, retrying row by row: {}",
                    batchError.getMostSpecificCause().getMessage());
            int ok = 0;
            for (BangumiEpisodeEntity entity : batch) {
                try {
                    upsertService.upsertEpisodeBatch(List.of(entity));
                    ok++;
                } catch (DataIntegrityViolationException rowError) {
                    log.warn(
                            "Skip episode id={}: {}",
                            entity.getId(),
                            rowError.getMostSpecificCause().getMessage());
                }
            }
            return ok;
        }
    }

    private <T> long readAndUpsert(
            BufferedReader reader,
            int batchSize,
            long maxBatchBytes,
            long delayMs,
            LineParser<T> parser,
            BatchConsumer<T> consumer) throws Exception {
        long total = 0;
        List<T> batch = new ArrayList<>(Math.min(batchSize, 1024));
        long batchBytes = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            long lineBytes = estimateLineBytes(line);
            if (!batch.isEmpty() && lineBytes > maxBatchBytes - batchBytes) {
                consumer.accept(batch);
                total += batch.size();
                batch.clear();
                batchBytes = 0;
                pause(delayMs);
            }
            batch.add(parser.parse(line));
            batchBytes += lineBytes;
            if (batch.size() >= batchSize || batchBytes >= maxBatchBytes) {
                consumer.accept(batch);
                total += batch.size();
                batch.clear();
                batchBytes = 0;
                pause(delayMs);
            }
        }
        if (!batch.isEmpty()) {
            consumer.accept(batch);
            total += batch.size();
        }
        return total;
    }

    private static void pause(long delayMs) throws InterruptedException {
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
    }

    private static long estimateLineBytes(String line) {
        // 使用字符数估算，避免为统计字节数再生成一份完整 byte[]。
        return line.length() * 2L + 256;
    }

    private interface LineParser<T> {
        T parse(String line) throws Exception;
    }

    private interface BatchConsumer<T> {
        void accept(List<T> batch);
    }
}
