package com.ligg.flowclient.service;

import com.ligg.common.thirdparty.bangumi.response.SubjectEpisodesDto;
import com.ligg.common.vo.bangumi.SearchSuggestionsVo;

public interface BangumiService {

    /**
     * 获取番剧剧集列表
     */
    SubjectEpisodesDto getEpisodes(Integer subjectId, int limit, int offset);

    /**
     * 获取搜索建议
     */
    SearchSuggestionsVo getSearchSuggestions(String keyword, int type, int limit);
}
