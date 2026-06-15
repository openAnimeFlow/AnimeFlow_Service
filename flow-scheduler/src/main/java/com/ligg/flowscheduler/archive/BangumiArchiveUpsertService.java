package com.ligg.flowscheduler.archive;

import com.ligg.common.entity.*;
import com.ligg.flowscheduler.config.BangumiArchiveSyncProperties;
import com.ligg.flowscheduler.mapper.BangumiArchiveUpsertMapper;
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

    public int batchSize() {
        return properties.getBatchSize();
    }

    public long batchDelayMs() {
        return properties.getBatchDelayMs();
    }
}
