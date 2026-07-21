package com.ligg.common.thirdparty.bangumi.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ligg.common.thirdparty.bangumi.enums.SubjectSearchSort;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Bangumi {@code POST /p1/search/subjects} 请求体。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchSubjectsBody {

    @NotBlank(message = "搜索关键词不能为空")
    private String keyword;

    /**
     * 排序方式，默认 {@link SubjectSearchSort#HEAT}
     */
    private SubjectSearchSort sort;

    private SearchSubjectsFilter filter;

    public SearchSubjectsBody toUpstreamBody() {
        SearchSubjectsBody upstream = new SearchSubjectsBody();
        upstream.keyword = keyword;
        if (sort != null) {
            upstream.sort = sort;
        }
        if (filter != null) {
            SearchSubjectsFilter upstreamFilter = filter.toUpstreamFilter();
            if (upstreamFilter != null) {
                upstream.filter = upstreamFilter;
            }
        }
        return upstream;
    }
}
