package com.ligg.common.thirdparty;

import lombok.Data;

import java.util.List;

@Data
public class BangumiRating {

    private Integer rank;
    private List<Integer> count;
    private Double score;
    private Integer total;
}
