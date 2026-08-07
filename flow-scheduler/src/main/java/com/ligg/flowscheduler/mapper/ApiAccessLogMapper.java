package com.ligg.flowscheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ligg.common.entity.ApiAccessLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 接口访问日志批量写入 Mapper。 */
@Mapper
public interface ApiAccessLogMapper extends BaseMapper<ApiAccessLogEntity> {

    int insertBatch(@Param("logs") List<ApiAccessLogEntity> logs);
}
