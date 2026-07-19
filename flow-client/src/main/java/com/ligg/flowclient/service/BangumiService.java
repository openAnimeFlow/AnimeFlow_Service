package com.ligg.flowclient.service;

import com.ligg.common.thirdparty.bangumi.request.SearchSubjectsBody;
import com.ligg.common.thirdparty.bangumi.response.SubjectEpisodesDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectsDto;
import com.ligg.common.vo.bangumi.SearchSuggestionsVo;
import com.ligg.common.vo.bangumi.SubjectDetailVo;
import com.ligg.common.vo.bangumi.SubjectRelationsVo;
import com.ligg.common.vo.bangumi.SubjectsVo;

public interface BangumiService {

    /**
     * 条目详情
     */
    SubjectDetailVo getSubjectInfo(Integer subjectId, long userId);

    /**
     * 获取番剧剧集列表
     */
    SubjectEpisodesDto getEpisodes(Integer subjectId, int limit, int offset, long userId);

    /**
     * 获取搜索建议
     */
    SearchSuggestionsVo getSearchSuggestions(String keyword, int type, int limit);

    /**
     * 搜索条目。请求参数兼容 Bangumi 搜索 API，实际读取本地搜索索引。
     *
     * @param flowAccessToken 可选 Flow JWT
     */
    SubjectsDto searchSubjects(SearchSubjectsBody body, int limit, int offset, String flowAccessToken);

    /**
     * 获取关联条目
     */
    SubjectRelationsVo getRelatedSubjects(Integer subjectId, int limit, int offset, int type, String bangumiAccessToken);

    /**
     * 获取相似条目推荐
     */
    SubjectsVo getRecommendedSubjects(Integer subjectId, int limit, int offset);
}
