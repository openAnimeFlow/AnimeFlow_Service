package com.ligg.common.model;

/**
 * 三档图片 URL（small / medium / large），Bangumi 封面与用户头像等结构通用。
 */
public interface ImageUrls {

    String getSmall();

    void setSmall(String url);

    String getMedium();

    void setMedium(String url);

    String getLarge();

    void setLarge(String url);
}
