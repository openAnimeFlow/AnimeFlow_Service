/**
 * @author Ligg
 */
package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("bangumi_subject_character")
public class BangumiSubjectCharacterEntity {

    /**
     * 角色 ID
     */
    private Integer characterId;

    /**
     * 作品 ID
     */
    private Integer subjectId;

    /**
     * 角色类型：1主角 2配角 3客串
     */
    private Integer type;

    /**
     * 角色列表排序
     */
    @TableField("`order`")
    private Integer order;
}
