package com.ligg.flowclient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ligg.flowclient.module.entity.CollectionSyncConflictEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CollectionSyncConflictMapper extends BaseMapper<CollectionSyncConflictEntity> {
    @Select("SELECT * FROM user_bgm_collection_sync_item WHERE task_id=#{taskId} AND user_id=#{userId} AND status='CONFLICT' ORDER BY id LIMIT #{limit} OFFSET #{offset}")
    List<CollectionSyncConflictEntity> selectConflicts(@Param("userId") Long userId, @Param("taskId") Long taskId,
                                                       @Param("limit") int limit, @Param("offset") int offset);
}
