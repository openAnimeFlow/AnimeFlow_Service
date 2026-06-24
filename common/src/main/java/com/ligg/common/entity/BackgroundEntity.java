/**
 * @author Ligg
 * @date 2026/6/24
 */
package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 背景图片表，数据来自 animeFlow-assets/background-image 资源仓库。
 */
@Data
@NoArgsConstructor
@TableName("background")
public class BackgroundEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 背景图片 Raw GitHub URL
     */
    private String image;

    /**
     * 文件名
     */
    private String name;
}
