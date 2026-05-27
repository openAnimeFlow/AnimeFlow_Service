package com.ligg.common.thirdparty;

import com.ligg.common.model.CoverImages;
import lombok.Data;

@Data
public class BangumiSubject {

    private Integer id;
    private String name;
    private String nameCN;
    private Integer type;
    private String info;
    private BangumiRating rating;
    private Boolean locked;
    private Boolean nsfw;
    private CoverImages images;
}
