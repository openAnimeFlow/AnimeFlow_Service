package com.ligg.flowclient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ligg.common.entity.UserBgmCollectionEntity;
import com.ligg.flowclient.module.dto.UserBgmCollectionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserBgmCollectionMapper extends BaseMapper<UserBgmCollectionEntity> {

    long countByUserFilter(@Param("userId") Long userId,
                           @Param("type") int type,
                           @Param("subjectType") int subjectType);

    List<UserBgmCollectionRow> selectPageByUserFilter(@Param("userId") Long userId,
                                                      @Param("type") int type,
                                                      @Param("subjectType") int subjectType,
                                                      @Param("limit") int limit,
                                                      @Param("offset") int offset);
}
