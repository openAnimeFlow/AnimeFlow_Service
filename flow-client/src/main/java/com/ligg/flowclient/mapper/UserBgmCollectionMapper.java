package com.ligg.flowclient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ligg.common.entity.UserBgmCollectionEntity;
import com.ligg.flowclient.module.dto.UserBgmCollectionRow;
import com.ligg.flowclient.module.dto.UserSubjectInterestRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserBgmCollectionMapper extends BaseMapper<UserBgmCollectionEntity> {

    int updateLocal(@Param("row") UserBgmCollectionEntity row);

    int updateRemoteState(@Param("row") UserBgmCollectionEntity row);

    List<UserBgmCollectionEntity> selectPendingUploads();

    @Select("SELECT COUNT(*) FROM user_bgm_collection_sync_item i JOIN user_bgm_collection_sync_task t ON t.id=i.task_id WHERE i.user_id=#{userId} AND i.subject_id=#{subjectId} AND i.status IN ('CONFLICT','RESOLUTION_PENDING','APPLY_PENDING','FAILED') AND t.status NOT IN ('CANCELLED','SUCCESS')")
    long countBlockedSyncItems(@Param("userId") Long userId, @Param("subjectId") int subjectId);

    @Select("SELECT COUNT(*) FROM user_bgm_collection_sync_task WHERE user_id=#{userId} AND subject_type=#{subjectType} AND status IN ('QUEUED','RUNNING','PARTIAL_FAILED')")
    long countActiveSyncTasks(@Param("userId") Long userId, @Param("subjectType") int subjectType);

    long countByUserFilter(@Param("userId") Long userId,
                           @Param("type") int type,
                           @Param("subjectType") int subjectType,
                           @Param("keyword") String keyword);

    List<UserBgmCollectionRow> selectPageByUserFilter(@Param("userId") Long userId,
                                                      @Param("type") int type,
                                                      @Param("subjectType") int subjectType,
                                                      @Param("keyword") String keyword,
                                                      @Param("limit") int limit,
                                                      @Param("offset") int offset);

    /**
     * 获取用户条目评价
     */
    UserSubjectInterestRow selectUserSubjectInterest(@Param("userId") Long userId,
                                                    @Param("subjectId") Integer subjectId);

}
