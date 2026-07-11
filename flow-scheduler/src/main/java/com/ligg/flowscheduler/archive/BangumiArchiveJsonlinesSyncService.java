package com.ligg.flowscheduler.archive;

import com.ligg.common.entity.BangumiEpisodeEntity;
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
 * 流式读取 jsonlines 文件，分批解析并写入数据库。
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
        int batchSize = upsertService.batchSize();
        long delayMs = upsertService.batchDelayMs();
        long total;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            total = switch (dataType) {
                case SUBJECT -> readAndUpsert(reader, batchSize, delayMs, lineParser::parseSubject,
                        upsertService::upsertSubjectBatch);
                case CHARACTER -> readAndUpsert(reader, batchSize, delayMs, lineParser::parseCharacter,
                        upsertService::upsertCharacterBatch);
                case PERSON -> readAndUpsert(reader, batchSize, delayMs, lineParser::parsePerson,
                        upsertService::upsertPersonBatch);
                case EPISODE -> readAndUpsertEpisodes(reader, batchSize, delayMs);
                case PERSON_CHARACTER -> readAndUpsert(reader, batchSize, delayMs, lineParser::parsePersonCharacter,
                        upsertService::upsertPersonCharacterBatch);
                case PERSON_RELATION -> readAndUpsert(reader, batchSize, delayMs, lineParser::parsePersonRelation,
                        upsertService::upsertPersonRelationBatch);
                case SUBJECT_CHARACTER -> readAndUpsert(reader, batchSize, delayMs, lineParser::parseSubjectCharacter,
                        upsertService::upsertSubjectCharacterBatch);
                case SUBJECT_PERSON -> readAndUpsert(reader, batchSize, delayMs, lineParser::parseSubjectPerson,
                        upsertService::upsertSubjectPersonBatch);
                case SUBJECT_RELATION -> readAndUpsert(reader, batchSize, delayMs, lineParser::parseSubjectRelation,
                        upsertService::upsertSubjectRelationBatch);
            };
        }

        log.info("Synced {} rows from {} ({})", total, file.getFileName(), dataType.getFileSuffix());
    }

    private long readAndUpsertEpisodes(BufferedReader reader, int batchSize, long delayMs) throws Exception {
        long total = 0;
        long skipped = 0;
        List<BangumiEpisodeEntity> batch = new ArrayList<>(batchSize);
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
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
            if (batch.size() >= batchSize) {
                total += upsertEpisodeBatchSkippingInvalid(batch);
                batch.clear();
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
            long delayMs,
            LineParser<T> parser,
            BatchConsumer<T> consumer) throws Exception {
        long total = 0;
        List<T> batch = new ArrayList<>(batchSize);
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            batch.add(parser.parse(line));
            if (batch.size() >= batchSize) {
                consumer.accept(batch);
                total += batch.size();
                batch.clear();
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

    private interface LineParser<T> {
        T parse(String line) throws Exception;
    }

    private interface BatchConsumer<T> {
        void accept(List<T> batch);
    }
}
