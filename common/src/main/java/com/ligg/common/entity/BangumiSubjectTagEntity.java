package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bangumi 条目标签索引行。
 *
 * <p>标签仍保留在 {@code bangumi_subject.tags/meta_tags} 中作为归档原文；本表用于索引和推荐查询。</p>
 */
@Data
@NoArgsConstructor
@TableName("bangumi_subject_tag")
public class BangumiSubjectTagEntity {

    /** 普通 tags。 */
    public static final int TYPE_TAG = 1;

    /** meta_tags。 */
    public static final int TYPE_META_TAG = 2;

    private Integer subjectId;

    private Integer tagType;

    private String tagName;
}
