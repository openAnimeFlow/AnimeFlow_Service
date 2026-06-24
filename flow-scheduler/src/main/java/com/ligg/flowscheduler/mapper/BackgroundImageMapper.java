package com.ligg.flowscheduler.mapper;

import com.ligg.common.entity.BackgroundEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * background 表 Mapper，基于 name 唯一索引进行 upsert。
 *
 * @author Ligg
 */
@Mapper
public interface BackgroundImageMapper {

    /**
     * 批量 upsert：name 冲突时更新 image 字段。
     */
    void upsertBatch(@Param("list") List<BackgroundEntity> list);

    /**
     * 删除 name 不在指定列表中的记录（资源仓库已移除的图片）。
     */
    void deleteByNameNotIn(@Param("names") List<String> names);
}
