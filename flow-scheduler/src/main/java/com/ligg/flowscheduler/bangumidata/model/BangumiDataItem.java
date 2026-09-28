package com.ligg.flowscheduler.bangumidata.model;

import lombok.Data;

/** bangumi-data 中的单条番组记录。 */
@Data
public class BangumiDataItem {
    private String itemKey;
    private String bangumiId;
    private String title;
    private String type;
    private String lang;
    private String officialSite;
    private String begin;
    private String end;
    private String rawData;
    private String syncVersion;
}
