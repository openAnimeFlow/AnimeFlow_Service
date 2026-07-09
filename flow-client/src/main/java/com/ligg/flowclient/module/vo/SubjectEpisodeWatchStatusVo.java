package com.ligg.flowclient.module.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class SubjectEpisodeWatchStatusVo {

    private Integer subjectId;

    private List<Long> watchedEpisodeIds;
}
