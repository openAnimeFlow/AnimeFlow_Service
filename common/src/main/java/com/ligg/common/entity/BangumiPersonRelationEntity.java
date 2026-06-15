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
@TableName("bangumi_person_relation")
public class BangumiPersonRelationEntity {

    /**
     * prsn 现实人物 / crt 虚拟角色
     */
    private String personType;

    /**
     * 人物/角色 ID
     */
    private Integer personId;

    /**
     * 关联人物/角色 ID
     */
    private Integer relatedPersonId;

    /**
     * 关联类型
     */
    private Integer relationType;

    /**
     * 是否剧透
     */
    private Boolean spoiler;

    /**
     * 是否已结束
     */
    private Boolean ended;
}
