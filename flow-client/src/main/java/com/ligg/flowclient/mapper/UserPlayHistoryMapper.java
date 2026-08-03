package com.ligg.flowclient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ligg.common.entity.UserPlayHistoryEntity;
import com.ligg.flowclient.module.dto.UserPlayHistoryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserPlayHistoryMapper extends BaseMapper<UserPlayHistoryEntity> {

    int upsert(@Param("row") UserPlayHistoryEntity row);

    List<UserPlayHistoryRow> selectPageByUser(@Param("userId") Long userId,
                                              @Param("limit") int limit,
                                              @Param("offset") int offset);

    UserPlayHistoryRow selectByUserAndSubject(@Param("userId") Long userId,
                                              @Param("subjectId") Integer subjectId);

    int clearProgress(@Param("userId") Long userId,
                      @Param("subjectId") Integer subjectId);
}
