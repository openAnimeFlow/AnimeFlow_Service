package com.ligg.flowclient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ligg.common.entity.BangumiSubjectEntity;
import com.ligg.flowclient.module.dto.SearchSuggestionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BangumiSubjectMapper extends BaseMapper<BangumiSubjectEntity> {

    List<SearchSuggestionRow> selectSearchSuggestions(@Param("keyword") String keyword,
                                                    @Param("type") int type,
                                                    @Param("limit") int limit);
}
