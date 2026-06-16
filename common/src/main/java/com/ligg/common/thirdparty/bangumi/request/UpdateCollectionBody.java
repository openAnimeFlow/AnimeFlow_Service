package com.ligg.common.thirdparty.bangumi.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Bangumi {@code PUT /p1/collections/subjects/{subjectId}} 请求体。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateCollectionBody {

    private Integer type;

    private Integer rate;

    @JsonProperty("private")
    private Boolean private_;

    private Boolean progress;

    private String comment;

    private List<String> tags;
}
