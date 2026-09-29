package com.ligg.flowclient.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BangumiDataItemMapper {

    @Select("""
            SELECT JSON_UNQUOTE(site.id)
            FROM bangumi_data_item b
            JOIN JSON_TABLE(
                b.raw_data,
                '$.sites[*]' COLUMNS (
                    site_key VARCHAR(64) PATH '$.site',
                    id JSON PATH '$.id'
                )
            ) AS site
            WHERE b.bangumi_id = #{bangumiId}
              AND site.site_key = 'tmdb'
            ORDER BY b.item_key
            LIMIT 1
            """)
    String selectTmdbIdByBangumiId(@Param("bangumiId") String bangumiId);
}
