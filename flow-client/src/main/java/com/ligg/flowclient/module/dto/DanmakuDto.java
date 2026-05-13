package com.ligg.flowclient.module.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class DanmakuDto {

    @NotNull(message = "弹幕剧集参数不合法")
    private Long episodeId;

    /**
     * 弹幕内容
     */
    @NotBlank(message = "弹幕内容不能为空")
    private String comment;


    /**
     * 弹幕出现时间，单位为秒
     */
    @NotNull
    @Min(value = 0)
    private Double time;

    /**
     * 弹幕颜色，使用十六进制表示
     */
    @NotNull
    @Max(value = 16777215, message = "颜色值不能超过16777215(0xFFFFFF)")
    private Integer color;

    /**
     * 弹幕类型，1为滚动弹幕，4为底部弹幕，5为顶部弹幕
     */
    @NotNull
    @NotNull(message = "弹幕类型不能为空")
    @Min(value = 1, message = "弹幕类型不合法")
    @Max(value = 5, message = "弹幕类型不合法")
    private Integer type;
}
