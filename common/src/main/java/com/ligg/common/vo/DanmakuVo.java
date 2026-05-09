package com.ligg.common.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DanmakuVo {

    /**
     * 弹幕ID
     */
    private Long cid;

    /**
     * (弹幕时间,弹幕类型,弹幕颜色,弹幕来源)
     */
    private String p;

    /**
     * 弹幕内容
     */
    private String m;
}
