package com.ligg.flowclient.service;

import com.ligg.common.thirdparty.bangumi.response.SubjectEpisodesDto;

public interface BangumiService {

    /**
     * 获取番剧剧集列表
     */
    SubjectEpisodesDto getEpisodes(Integer subjectId, int limit, int offset);
}
