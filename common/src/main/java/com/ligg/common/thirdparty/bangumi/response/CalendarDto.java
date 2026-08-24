/**
 * @author Ligg
 * @date 2026/5/28 01:52
 */
package com.ligg.common.thirdparty.bangumi.response;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ligg.common.thirdparty.bangumi.model.BangumiSubject;
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
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private EpisodeSummary latestEpisode;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private EpisodeSummary nextEpisode;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Integer episodeCount;

        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class EpisodeSummary {
            private Integer id;
            private Integer sort;
            private String name;
            private String nameCn;
            private String airdate;
            private String duration;
        }
    }
}
