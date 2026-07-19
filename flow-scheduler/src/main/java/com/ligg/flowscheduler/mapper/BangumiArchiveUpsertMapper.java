package com.ligg.flowscheduler.mapper;

import com.ligg.common.entity.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Bangumi Archive 各表的批量 upsert（insert on duplicate key update）Mapper。
 *
 * @author Ligg
 */
@Mapper
public interface BangumiArchiveUpsertMapper {

    void upsertCharacterBatch(@Param("list") List<BangumiCharacterEntity> list);

    void upsertEpisodeBatch(@Param("list") List<BangumiEpisodeEntity> list);

    void upsertPersonBatch(@Param("list") List<BangumiPersonEntity> list);

    void upsertSubjectBatch(@Param("list") List<BangumiSubjectEntity> list);

    void upsertSubjectSearchBatch(@Param("list") List<BangumiSubjectSearchEntity> list);

    void upsertPersonCharacterBatch(@Param("list") List<BangumiPersonCharacterEntity> list);

    void upsertPersonRelationBatch(@Param("list") List<BangumiPersonRelationEntity> list);

    void upsertSubjectCharacterBatch(@Param("list") List<BangumiSubjectCharacterEntity> list);

    void upsertSubjectPersonBatch(@Param("list") List<BangumiSubjectPersonEntity> list);

    void upsertSubjectRelationBatch(@Param("list") List<BangumiSubjectRelationEntity> list);
}
