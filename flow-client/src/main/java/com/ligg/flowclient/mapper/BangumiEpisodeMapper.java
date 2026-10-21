package com.ligg.flowclient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ligg.common.entity.BangumiEpisodeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jspecify.annotations.Nullable;

@Mapper
public interface BangumiEpisodeMapper extends BaseMapper<BangumiEpisodeEntity> {

    /** 按条目内正片播放顺序计算从 1 开始的集号，剧集不存在、不属于条目或不是正片时返回 null。 */
    @Select("""
            SELECT COUNT(previous.id) + 1
            FROM bangumi_episode target
            LEFT JOIN bangumi_episode previous
              ON previous.subject_id = target.subject_id
             AND previous.type = 0
             AND (previous.sort < target.sort OR (previous.sort = target.sort AND previous.id < target.id))
            WHERE target.subject_id = #{subjectId} AND target.id = #{episodeId} AND target.type = 0
            GROUP BY target.id
            """)
    @Nullable Integer selectEpisodeNumber(@Param("subjectId") int subjectId, @Param("episodeId") int episodeId);
}
