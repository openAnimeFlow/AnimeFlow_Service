package com.ligg.module.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DanmakuDto {

    private Long id;

    /**
     * 弹幕内容
     */
    private String message;

    /**
     * 弹幕出现时间，单位为秒
     */
    private Double time;

    /**
     * 弹幕颜色，使用十六进制表示
     */
    private Integer color;

    /**
     * 弹幕类型，1为滚动弹幕，4为底部弹幕，5为顶部弹幕
     */
    private Integer type;

    /**
     * 弹幕来源([BiliBili], [Gamer])
     */
    private String source;

}
