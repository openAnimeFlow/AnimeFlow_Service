package com.ligg.flowclient.module.vo;

import com.ligg.common.model.CoverImages;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当前正在被用户播放的番剧。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchingSubjectVo {

    private Integer subjectId;
    private String name;
    private String nameCn;
    private CoverImages images;
    private OnlineCountVo online;
}
