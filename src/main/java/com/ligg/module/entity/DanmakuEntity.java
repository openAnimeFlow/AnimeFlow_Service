package com.ligg.module.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("danmaku")
public class DanmakuEntity {
    @TableId(type = IdType.NONE)
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

    /**
     * 数据创建时间
     */
    private LocalDateTime createTime;
}
