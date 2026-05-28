package com.ligg.common.thirdparty;

import lombok.Data;

import java.util.List;

/**
 * Bangumi 条目列表 {@code GET /p1/subjects} 响应。
 */
@Data
public class SubjectsDto {

    private List<BangumiSubject> data;
    private Integer total;
}
