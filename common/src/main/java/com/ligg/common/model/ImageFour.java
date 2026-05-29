package com.ligg.common.model;

import lombok.Data;

/**
 * Bangumi 角色、人物等常用的四档图片 URL（large / medium / small / grid），不含 common。
 */
@Data
public class ImageFour implements ImageUrls {

    private String large;
    private String medium;
    private String small;
    private String grid;
}
