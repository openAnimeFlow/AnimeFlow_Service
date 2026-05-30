package com.ligg.common.vo.bangumi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Bangumi 用户主页 {@code #userStats_all} 解析出的统计卡片数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BgmUserStatisticsVo {

    private List<StatisticVo> statistics = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatisticVo {
        private String value;
        private String name;
    }
}
