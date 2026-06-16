/**
 * @author Ligg
 * @date 2026/6/5 04:50
 */
package com.ligg.flowclient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ligg.common.entity.UserEntity;
import com.ligg.flowclient.module.dto.UserProfileRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    UserProfileRow selectProfileById(@Param("userId") Long userId);
}
