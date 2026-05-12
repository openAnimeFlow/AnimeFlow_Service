package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("danmaku")
public class DanmakuEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的剧集ID
     */
    private Long episodeId;

    /**
     * 弹幕内容
     */
    private String comment;

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
     * bgm_id
     */
    private Integer bgmId;

    /**
     * 弹幕来源([BiliBili], [Gamer])
     */
    private String source;

    /**
     * 数据创建时间
     */
    private LocalDateTime createTime;
}
