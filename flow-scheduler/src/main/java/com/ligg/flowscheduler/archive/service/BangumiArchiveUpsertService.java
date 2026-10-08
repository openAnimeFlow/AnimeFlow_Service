package com.ligg.flowscheduler.archive.service;

import com.ligg.common.entity.*;
import com.ligg.flowscheduler.archive.config.BangumiArchiveSyncProperties;
import com.ligg.flowscheduler.archive.mapper.BangumiArchiveUpsertMapper;
import com.ligg.flowscheduler.archive.BangumiSubjectSearchIndexBuilder;
import com.ligg.flowscheduler.archive.BangumiSubjectTagIndexBuilder;
import com.ligg.flowscheduler.archive.BangumiSubjectTagIndexBuilder.SubjectTags;
import com.ligg.flowscheduler.archive.ArchiveBatchWriter;
import com.ligg.flowscheduler.archive.ArchiveDataType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 按批次调用 Mapper 执行 upsert，每批独立事务以降低长事务对线上的影响。
 *
 * @author Ligg
 */
@Service
@RequiredArgsConstructor
public class BangumiArchiveUpsertService {

    private final BangumiArchiveUpsertMapper upsertMapper;
    private final BangumiArchiveSyncProperties properties;
    private final BangumiSubjectSearchIndexBuilder searchIndexBuilder;
    private final BangumiSubjectTagIndexBuilder subjectTagIndexBuilder;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertCharacterBatch(List<BangumiCharacterEntity> batch) {
        if (batch.isEmpty()) {
            return;
        }
        upsertMapper.upsertCharacterBatch(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertEpisodeBatch(List<BangumiEpisodeEntity> batch) {
        if (batch.isEmpty()) {
            return;
        }
        upsertMapper.upsertEpisodeBatch(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertPersonBatch(List<BangumiPersonEntity> batch) {
        if (batch.isEmpty()) {
            return;
        }
        upsertMapper.upsertPersonBatch(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertSubjectBatch(List<BangumiSubjectEntity> batch) {
        if (batch.isEmpty()) {
            return;
        }
        upsertMapper.upsertSubjectBatch(batch);
        upsertMapper.deleteSubjectTagsBySubjectIds(batch.stream().map(BangumiSubjectEntity::getId).toList());
        ArchiveBatchWriter<BangumiSubjectTagEntity> tagWriter = new ArchiveBatchWriter<>(
                properties.getTagBatchSize(), maxBatchBytes(), upsertMapper::insertSubjectTagBatch);
        ArchiveBatchWriter<BangumiSubjectSearchEntity> searchWriter = new ArchiveBatchWriter<>(
                batchSize(ArchiveDataType.SUBJECT), maxBatchBytes(), upsertMapper::upsertSubjectSearchBatch);
        for (BangumiSubjectEntity subject : batch) {
            SubjectTags tags = subjectTagIndexBuilder.extractTags(subject);
            subjectTagIndexBuilder.forEachTag(subject.getId(), tags,
                    tag -> tagWriter.add(tag, 64L + stringBytes(tag.getTagName())));
            BangumiSubjectSearchEntity search = searchIndexBuilder.build(subject, tags);
            searchWriter.add(search, estimateSearchBytes(search));
        }
        tagWriter.flush();
        searchWriter.flush();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertPersonCharacterBatch(List<BangumiPersonCharacterEntity> batch) {
        if (batch.isEmpty()) {
            return;
        }
        upsertMapper.upsertPersonCharacterBatch(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertPersonRelationBatch(List<BangumiPersonRelationEntity> batch) {
        if (batch.isEmpty()) {
            return;
        }
        upsertMapper.upsertPersonRelationBatch(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertSubjectCharacterBatch(List<BangumiSubjectCharacterEntity> batch) {
        if (batch.isEmpty()) {
            return;
        }
        upsertMapper.upsertSubjectCharacterBatch(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertSubjectPersonBatch(List<BangumiSubjectPersonEntity> batch) {
        if (batch.isEmpty()) {
            return;
        }
        upsertMapper.upsertSubjectPersonBatch(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertSubjectRelationBatch(List<BangumiSubjectRelationEntity> batch) {
        if (batch.isEmpty()) {
            return;
        }
        upsertMapper.upsertSubjectRelationBatch(batch);
    }

    public int batchSize(ArchiveDataType dataType) {
        Integer configured = properties.getBatchSizes().get(dataType);
        return Math.max(1, configured != null ? configured : properties.getBatchSize());
    }

    public long maxBatchBytes() {
        return Math.max(1, properties.getMaxBatchBytes());
    }

    public long batchDelayMs() {
        return properties.getBatchDelayMs();
    }

    private static long estimateSearchBytes(BangumiSubjectSearchEntity search) {
        return 128L + stringBytes(search.getName()) + stringBytes(search.getNameCn())
                + stringBytes(search.getAliases()) + stringBytes(search.getTagsText())
                + stringBytes(search.getMetaTagsText()) + stringBytes(search.getSearchText());
    }

    private static long stringBytes(String value) {
        return value == null ? 0 : value.length() * 2L;
    }
}
