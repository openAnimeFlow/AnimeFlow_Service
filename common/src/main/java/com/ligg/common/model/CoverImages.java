package com.ligg.common.model;

import lombok.Data;

/**
 * Bangumi 等第三方常用的五档封面图 URL（large / common / medium / small / grid）。
 */
@Data
public class CoverImages implements ImageUrls {

    private String large;
    private String common;
    private String medium;
    private String small;
    private String grid;
}
