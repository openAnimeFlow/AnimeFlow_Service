package com.ligg.common.vo.bangumi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ligg.common.thirdparty.bangumi.response.CalendarDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 番周表。
 * days 结构与 Bangumi 日历一致：1=周一 … 7=周日。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SeasonCalendarVo extends CalendarDto {

    private Integer year;
    private Integer month;
    private String seasonName;
    private Integer total;

    /**
     * 无法确定放送星期的条目，正常数据中应为空列表。
     */
    private List<CalendarDto.Entry> unknown;
}
