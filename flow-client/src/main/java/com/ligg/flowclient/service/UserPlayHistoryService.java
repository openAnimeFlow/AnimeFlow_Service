package com.ligg.flowclient.service;

import com.ligg.flowclient.module.dto.SavePlayHistoryDto;
import com.ligg.flowclient.module.vo.PlayHistoryVo;

import java.util.List;

public interface UserPlayHistoryService {

    void save(String accessToken, SavePlayHistoryDto dto);

    List<PlayHistoryVo> list(String accessToken, int limit, int offset);

    PlayHistoryVo getBySubject(String accessToken, int subjectId);

    void clearProgress(String accessToken, int subjectId);
}
