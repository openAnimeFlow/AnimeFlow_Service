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
@TableName("bangumi_subject_person")
public class BangumiSubjectPersonEntity {

    /**
     * 人物 ID
     */
    private Integer personId;

    /**
     * 作品 ID
     */
    private Integer subjectId;

    /**
     * 担任职位
     */
    private Integer position;

    /**
     * 参与章节
     */
    private String appearEps;
}
