/**
 * @author Ligg
 * @date 2026/6/15 12:03
 */
package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("bangumi_person_character")
public class BangumiPersonCharacterEntity {

    /**
     * 人物 ID
     */
    private Integer personId;

    /**
     * 条目 ID
     */
    private Integer subjectId;

    /**
     * 角色 ID
     */
    private Integer characterId;

    /**
     * 关联类型
     */
    private Integer type;

    /**
     * 概要
     */
    private String summary;
}
