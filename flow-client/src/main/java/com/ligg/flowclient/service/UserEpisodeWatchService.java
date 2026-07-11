package com.ligg.flowclient.service;

import com.ligg.flowclient.module.dto.UpdateEpisodeWatchDto;
import com.ligg.flowclient.module.vo.SubjectEpisodeWatchStatusVo;

import java.util.Set;

public interface UserEpisodeWatchService {

    void updateEpisodeWatch(String accessToken, int episodeId, UpdateEpisodeWatchDto dto);

    void markSubjectEpisodesWatched(String accessToken, int subjectId);

    SubjectEpisodeWatchStatusVo getSubjectWatchStatus(String accessToken, int subjectId);

    Set<Long> listWatchedEpisodeIds(long userId, int subjectId);
}
