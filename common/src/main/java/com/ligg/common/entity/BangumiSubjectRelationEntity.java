/**
 * @author Ligg
 * @date 2026/6/15 12:03
 */
package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("bangumi_subject_relation")
public class BangumiSubjectRelationEntity {

    /**
     * 作品 ID
     */
    private Integer subjectId;

    /**
     * 关联类型
     */
    private Integer relationType;

    /**
     * 关联作品 ID
     */
    private Integer relatedSubjectId;

    /**
     * 关联排序
     */
    @TableField("`order`")
    private Integer order;
}
