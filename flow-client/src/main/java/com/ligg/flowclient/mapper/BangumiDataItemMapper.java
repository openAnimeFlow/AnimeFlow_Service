package com.ligg.flowclient.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BangumiDataItemMapper {

    @Select("""
            SELECT JSON_EXTRACT(raw_data, '$.sites')
            FROM bangumi_data_item
            WHERE bangumi_id = #{bangumiId}
            ORDER BY item_key
            LIMIT 1
            """)
    String selectSitesByBangumiId(@Param("bangumiId") String bangumiId);
}
