package com.ligg.flowscheduler.bangumidata.model;

import lombok.Data;

/** bangumi-data 中播放、资讯及资源站点的元数据。 */
@Data
public class BangumiDataSiteMeta {
    private String siteKey;
    private String title;
    private String type;
    private String urlTemplate;
    private String regions;
    private String syncVersion;
}
