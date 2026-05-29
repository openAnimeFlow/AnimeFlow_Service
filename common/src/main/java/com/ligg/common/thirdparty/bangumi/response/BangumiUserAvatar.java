package com.ligg.common.thirdparty.bangumi.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ligg.common.model.ImageUrls;
import lombok.Data;

/**
 * Bangumi 用户头像（small / medium / large）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BangumiUserAvatar implements ImageUrls {

    private String small;
    private String medium;
    private String large;
}
