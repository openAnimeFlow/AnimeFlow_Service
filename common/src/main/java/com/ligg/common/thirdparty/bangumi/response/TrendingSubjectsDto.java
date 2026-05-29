package com.ligg.common.thirdparty.bangumi.response;

import com.ligg.common.thirdparty.bangumi.model.BangumiSubject;
import lombok.Data;

import java.util.List;

/**
 * Bangumi 趋势条目 {@code GET /p1/trending/subjects} 响应。
 */
@Data
public class TrendingSubjectsDto {

    private List<Item> data;
    private Integer total;

    @Data
    public static class Item {
        private BangumiSubject subject;
        private Integer count;
    }
}
