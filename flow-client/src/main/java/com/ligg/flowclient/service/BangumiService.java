package com.ligg.flowclient.service;

import com.ligg.common.thirdparty.bangumi.request.SearchSubjectsBody;
import com.ligg.common.thirdparty.bangumi.response.SubjectEpisodesDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectsDto;
import com.ligg.common.vo.bangumi.SearchSuggestionsVo;
import com.ligg.common.vo.bangumi.SubjectRelationsVo;

public interface BangumiService {

    /**
     * 获取番剧剧集列表
     */
    SubjectEpisodesDto getEpisodes(Integer subjectId, int limit, int offset);

    /**
     * 获取搜索建议
     */
    SearchSuggestionsVo getSearchSuggestions(String keyword, int type, int limit);

    /**
     * 搜索条目。已登录且绑定 Bangumi 时经 OAuth executor 携带 Bangumi token 搜索（含 interest），否则匿名搜索。
     *
     * @param flowAccessToken 可选 Flow JWT
     */
    SubjectsDto searchSubjects(SearchSubjectsBody body, int limit, int offset, String flowAccessToken);

    /**
     * 获取关联条目
     */
    SubjectRelationsVo getRelatedSubjects(Integer subjectId, int limit, int offset,int type);
}
