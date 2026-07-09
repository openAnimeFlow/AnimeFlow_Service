package com.ligg.flowclient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ligg.common.entity.UserEpisodeWatchEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserEpisodeWatchMapper extends BaseMapper<UserEpisodeWatchEntity> {

    @Insert("""
            insert into user_episode_watch (user_id, subject_id, episode_id, watch_status, watched_at)
            values (#{userId}, #{subjectId}, #{episodeId}, 1, current_timestamp)
            on duplicate key update
            subject_id = values(subject_id),
            watch_status = values(watch_status),
            watched_at = coalesce(watched_at, values(watched_at))
            """)
    int upsertWatched(@Param("userId") Long userId,
                      @Param("subjectId") Integer subjectId,
                      @Param("episodeId") Integer episodeId);

    @Update("""
            update user_episode_watch
            set watch_status = 0
            where user_id = #{userId}
              and episode_id = #{episodeId}
            """)
    int markUnwatched(@Param("userId") Long userId,
                      @Param("episodeId") Integer episodeId);
}
