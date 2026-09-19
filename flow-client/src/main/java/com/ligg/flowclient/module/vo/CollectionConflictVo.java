package com.ligg.flowclient.module.vo;

import lombok.Data;

@Data
public class CollectionConflictVo {
    private Long conflictId;
    private Integer subjectId;
    private Integer subjectType;
    private String subjectName;
    private String subjectImage;
    private Integer localType;
    private Integer remoteType;
    private Long localVersion;
    private Long conflictVersion;
    private String status;
}
