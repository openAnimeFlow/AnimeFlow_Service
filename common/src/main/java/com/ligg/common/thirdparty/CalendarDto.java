/**
 * @author Ligg
 * @date 2026/5/28 01:52
 */
package com.ligg.common.thirdparty;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bangumi 放送日历 {@code GET /p1/calendar} 响应。
 * 顶层 key 为星期（1=周一 … 7=周日），值为当日番剧列表。
 */
@Data
public class CalendarDto {

    @JsonIgnore
    private final Map<String, List<Entry>> days = new LinkedHashMap<>();

    @JsonAnySetter
    public void setDay(String weekday, List<Entry> entries) {
        days.put(weekday, entries);
    }

    @JsonAnyGetter
    public Map<String, List<Entry>> getDays() {
        return days;
    }

    @Data
    public static class Entry {
        private BangumiSubject subject;
        private Integer watchers;
    }
}
