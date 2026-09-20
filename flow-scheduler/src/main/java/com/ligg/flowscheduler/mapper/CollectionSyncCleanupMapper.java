package com.ligg.flowscheduler.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 收藏同步历史清理。 */
@Mapper
public interface CollectionSyncCleanupMapper {
    @Select("""
            SELECT id
            FROM user_bgm_collection_sync_task
            WHERE status IN ('SUCCESS', 'FAILED', 'CANCELLED')
              AND finished_at IS NOT NULL
              AND finished_at < #{before}
            ORDER BY id
            LIMIT #{limit}
            """)
    List<Long> selectExpiredTaskIds(@Param("before") LocalDateTime before, @Param("limit") int limit);

    @Delete("DELETE FROM user_bgm_collection_sync_item WHERE task_id=#{taskId}")
    int deleteItemsByTaskId(@Param("taskId") Long taskId);

    @Delete("""
            DELETE FROM user_bgm_collection_sync_task
            WHERE id=#{taskId}
              AND status IN ('SUCCESS', 'FAILED', 'CANCELLED')
              AND finished_at IS NOT NULL
              AND finished_at < #{before}
            """)
    int deleteExpiredTask(@Param("taskId") Long taskId, @Param("before") LocalDateTime before);
}
