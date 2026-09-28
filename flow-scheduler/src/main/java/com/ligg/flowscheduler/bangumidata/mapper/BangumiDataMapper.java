package com.ligg.flowscheduler.bangumidata.mapper;

import com.ligg.flowscheduler.bangumidata.model.BangumiDataItem;
import com.ligg.flowscheduler.bangumidata.model.BangumiDataSiteMeta;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BangumiDataMapper {
    void upsertItems(@Param("list") List<BangumiDataItem> items);
    void upsertSiteMeta(@Param("list") List<BangumiDataSiteMeta> sites);
    List<String> selectStaleItemKeys(@Param("version") String version, @Param("limit") int limit);
    int deleteItemsByKeys(@Param("keys") List<String> keys);
    List<String> selectStaleSiteKeys(@Param("version") String version, @Param("limit") int limit);
    int deleteSitesByKeys(@Param("keys") List<String> keys);
    int countItemsByVersion(@Param("version") String version);
    int countSitesByVersion(@Param("version") String version);
}
