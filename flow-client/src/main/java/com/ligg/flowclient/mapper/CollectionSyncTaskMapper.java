package com.ligg.flowclient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ligg.flowclient.module.entity.CollectionSyncTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CollectionSyncTaskMapper extends BaseMapper<CollectionSyncTaskEntity> {
    @Select("SELECT * FROM user_bgm_collection_sync_task WHERE user_id=#{userId} AND status IN ('QUEUED','RUNNING','WAITING_CONFLICT','PARTIAL_FAILED') ORDER BY id DESC LIMIT 1")
    CollectionSyncTaskEntity selectActive(@Param("userId") Long userId);
    @Select("SELECT * FROM user_bgm_collection_sync_task WHERE id=#{taskId} AND user_id=#{userId}")
    CollectionSyncTaskEntity selectOwned(@Param("userId") Long userId, @Param("taskId") Long taskId);
    @Select("SELECT * FROM user_bgm_collection_sync_task WHERE user_id=#{userId} AND request_id=#{requestId}")
    CollectionSyncTaskEntity selectRequest(@Param("userId") Long userId, @Param("requestId") String requestId);
    @Select("SELECT * FROM user_bgm_collection_sync_task WHERE user_id=#{userId} ORDER BY id DESC LIMIT 1")
    CollectionSyncTaskEntity selectLatest(@Param("userId") Long userId);
    @Select("SELECT * FROM user_bgm_collection_sync_task WHERE status IN ('QUEUED','RUNNING','PARTIAL_FAILED','WAITING_CONFLICT') ORDER BY COALESCE(heartbeat_at,created_at),id LIMIT 20")
    java.util.List<CollectionSyncTaskEntity> selectRecoverable();
}
