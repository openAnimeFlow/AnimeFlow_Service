/**
 * @author Ligg
 * @date 2026/5/5 10:59
 */
package com.ligg.api.bangumiapi;

import com.ligg.common.thirdparty.bangumi.enums.SubjectBrowseSort;
import com.ligg.common.thirdparty.bangumi.request.SearchSubjectsBody;
import com.ligg.common.thirdparty.bangumi.response.CalendarDto;
import com.ligg.common.thirdparty.bangumi.response.EpisodeCommentsDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectDetailDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectCharactersDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectEpisodesDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectCommentsDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectStaffPersonsDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectsDto;
import com.ligg.common.thirdparty.bangumi.response.TrendingSubjectsDto;
import com.ligg.common.vo.BangumiUserinfoVO;

public interface BangumiClient {
    /**
     * 获取当前用户信息
     */
    BangumiUserinfoVO getMe(String accessToken);

    /**
     * 获取每日放送
     */
    CalendarDto getCalendar();

    /**
     * 获取趋势条目
     *
     * @param type   条目类型，2=动画
     * @param limit  每页条数
     * @param offset 偏移量
     */
    TrendingSubjectsDto getTrendingSubjects(int type, int limit, int offset);

    /**
     * 获取条目列表
     *
     * @param sort  排序方式，如 rank
     * @param page  页码，从 1 开始
     * @param type  条目类型，2=动画
     * @param year  放送年份，可为空
     * @param month 放送月份，可为空
     */
    SubjectsDto getSubjects(SubjectBrowseSort sort, int page, int type, Integer year, Integer month);

    /**
     * 搜索条目；{@code accessToken} 为空时不带 Bearer，响应不含 {@code interest}。
     */
    SubjectsDto searchSubjects(SearchSubjectsBody body, int limit, int offset, String accessToken);

    /**
     * 获取条目详情；{@code accessToken} 为空时不带 Bearer，响应不含 {@code interest}。
     */
    SubjectDetailDto getSubject(int subjectId, String accessToken);

    /**
     * 获取条目章节列表
     */
    SubjectEpisodesDto getSubjectEpisodes(int subjectId, int limit, int offset);

    /**
     * 获取条目角色列表
     *
     * @param type 角色类型筛选，可为空
     */
    SubjectCharactersDto getSubjectCharacters(int subjectId, int limit, int offset, Integer type);

    /**
     * 获取条目制作人员列表
     */
    SubjectStaffPersonsDto getSubjectStaffPersons(int subjectId, int limit, int offset);

    /**
     * 获取条目评论列表
     */
    SubjectCommentsDto getSubjectComments(int subjectId, int limit, int offset);

    /**
     * 获取章节评论列表
     */
    EpisodeCommentsDto getEpisodeComments(long episodeId);
}
