package com.ligg.flowclient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ligg.common.entity.ForumCommentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ForumCommentMapper extends BaseMapper<ForumCommentEntity> {

    /**
     * 按一级评论 id 列表查询直接子回复：{@code parent_id} 落在这些 id 中。
     */
    List<ForumCommentEntity> selectRepliesByParentIds(@Param("parentIds") Collection<Long> parentIds);
}
