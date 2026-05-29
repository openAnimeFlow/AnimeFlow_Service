package com.ligg.common.thirdparty.bangumi.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * Bangumi {@code POST /p1/search/subjects} 请求体中的 {@code filter}。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchSubjectsFilter {

    private List<Integer> type;
    private List<String> tags;
    private List<String> metaTags;
    private List<String> date;
    private List<String> rating;
    private List<String> rank;
    private Boolean nsfw;

    /**
     * 所有字段均为空时返回 {@code null}。
     */
    public SearchSubjectsFilter toUpstreamFilter() {
        SearchSubjectsFilter upstream = new SearchSubjectsFilter();
        if (type != null && !type.isEmpty()) {
            upstream.type = type;
        }
        if (tags != null && !tags.isEmpty()) {
            upstream.tags = tags;
        }
        if (metaTags != null && !metaTags.isEmpty()) {
            upstream.metaTags = metaTags;
        }
        if (date != null && !date.isEmpty()) {
            upstream.date = date;
        }
        if (rating != null && !rating.isEmpty()) {
            upstream.rating = rating;
        }
        if (rank != null && !rank.isEmpty()) {
            upstream.rank = rank;
        }
        if (nsfw != null) {
            upstream.nsfw = nsfw;
        }
        if (upstream.type == null
                && upstream.tags == null
                && upstream.metaTags == null
                && upstream.date == null
                && upstream.rating == null
                && upstream.rank == null
                && upstream.nsfw == null) {
            return null;
        }
        return upstream;
    }
}
