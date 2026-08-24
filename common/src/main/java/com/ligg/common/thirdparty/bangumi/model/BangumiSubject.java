package com.ligg.common.thirdparty.bangumi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ligg.common.model.CoverImages;
import lombok.Data;

import java.util.List;

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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> metaTags;
}
