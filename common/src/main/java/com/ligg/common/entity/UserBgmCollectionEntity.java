package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("user_bgm_collection")
public class UserBgmCollectionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer subjectId;

    private String images;

    private Long bgmInterestId;

    private Integer rate;

    private Integer type;

    private String comment;

    private String tags;

    private Integer epStatus;

    private Integer volStatus;

    private Boolean isPrivate;

    private Long bgmUpdatedAt;

    private LocalDateTime syncTime;

    private LocalDateTime createTime;
}
