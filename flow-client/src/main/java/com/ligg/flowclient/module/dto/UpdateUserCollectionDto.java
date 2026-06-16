package com.ligg.flowclient.module.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
public class UpdateUserCollectionDto {

    @Min(1)
    @Max(5)
    private Integer type;

    @Min(0)
    @Max(10)
    private Integer rate;

    @JsonProperty("private")
    private Boolean private_;

    private Boolean progress;

    private String comment;

    private List<String> tags;

    /**
     * 条目大类（1漫画 2动画 3音乐 4游戏 6三次元），写入本地收藏表时使用，默认 2。
     */
    @Min(1)
    @Max(6)
    private Integer subjectType;

    public boolean hasUpdateField() {
        return type != null
                || rate != null
                || private_ != null
                || progress != null
                || comment != null
                || (tags != null && !tags.isEmpty());
    }
}
