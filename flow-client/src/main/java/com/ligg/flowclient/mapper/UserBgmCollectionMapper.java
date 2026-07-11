package com.ligg.flowclient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ligg.common.entity.UserBgmCollectionEntity;
import com.ligg.flowclient.module.dto.UserBgmCollectionRow;
import com.ligg.flowclient.module.dto.UserSubjectInterestRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserBgmCollectionMapper extends BaseMapper<UserBgmCollectionEntity> {

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
