/**
 * @author Ligg
 * @date 2026/5/28 02:07
 */
package com.ligg.flowclient.controller.bangumi;

import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.common.thirdparty.CalendarDto;
import com.ligg.common.thirdparty.SubjectDetailDto;
import com.ligg.common.thirdparty.SubjectEpisodesDto;
import com.ligg.common.thirdparty.SubjectsDto;
import com.ligg.common.thirdparty.TrendingSubjectsDto;
import com.ligg.common.utils.Utils;
import com.ligg.common.vo.bangumi.CalendarVo;
import com.ligg.common.vo.bangumi.SubjectDetailVo;
import com.ligg.common.vo.bangumi.SubjectEpisodesVo;
import com.ligg.common.vo.bangumi.SubjectsVo;
import com.ligg.common.vo.bangumi.TrendingSubjectsVo;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/bangumi")
@RequiredArgsConstructor
public class BangumiController {

    private final BangumiClient bangumiClient;

    /**
     * 获取每日放送
     */
    @GetMapping("/calendar")
    public Result<CalendarVo> calendar() {
        CalendarDto calendarDto = bangumiClient.getCalendar();
        for (List<CalendarDto.Entry> entries : calendarDto.getDays().values()) {
            if (entries == null) {
                continue;
            }
            for (CalendarDto.Entry entry : entries) {
                if (entry == null || entry.getSubject() == null) {
                    continue;
                }
                Utils.applyWsrvCdnInPlace(entry.getSubject().getImages());
            }
        }
        CalendarVo calendarVo = new CalendarVo();
        calendarVo.getDays().putAll(calendarDto.getDays());
        return Result.success(ResponseCode.SUCCESS, calendarVo);
    }

    /**
     * 条目列表（默认动画 type=2，按 rank 排序）
     */
    @GetMapping("/subjects")
    public Result<SubjectsVo> subjects(
            @RequestParam(defaultValue = "rank") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "2") int type,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        SubjectsDto dto = bangumiClient.getSubjects(sort, page, type, year, month);
        if (dto.getData() != null) {
            for (var subject : dto.getData()) {
                if (subject == null) {
                    continue;
                }
                Utils.applyWsrvCdnInPlace(subject.getImages());
            }
        }
        SubjectsVo vo = new SubjectsVo();
        BeanUtils.copyProperties(dto, vo);
        return Result.success(ResponseCode.SUCCESS, vo);
    }

    /**
     * 获取趋势条目（默认动画 type=2）
     */
    @GetMapping("/trending/subjects")
    public Result<TrendingSubjectsVo> trendingSubjects(
            @RequestParam(defaultValue = "2") int type,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        TrendingSubjectsDto dto = bangumiClient.getTrendingSubjects(type, limit, offset);
        if (dto.getData() != null) {
            for (TrendingSubjectsDto.Item item : dto.getData()) {
                if (item == null || item.getSubject() == null) {
                    continue;
                }
                Utils.applyWsrvCdnInPlace(item.getSubject().getImages());
            }
        }
        TrendingSubjectsVo vo = new TrendingSubjectsVo();
        BeanUtils.copyProperties(dto, vo);
        return Result.success(ResponseCode.SUCCESS, vo);
    }

    /**
     * 条目详情；携带 Authorization Bearer 时返回当前用户 {@code interest}，否则不含该字段。
     */
    @GetMapping("/subjects/{subjectId}")
    public Result<SubjectDetailVo> subjectDetail(
            @NotNull @PathVariable int subjectId,
            @RequestAttribute(name = AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE, required = false)
            String accessToken) {
        SubjectDetailDto dto = bangumiClient.getSubject(subjectId, accessToken);
        Utils.applyWsrvCdnInPlace(dto.getImages());
        SubjectDetailVo vo = new SubjectDetailVo();
        BeanUtils.copyProperties(dto, vo);
        return Result.success(ResponseCode.SUCCESS, vo);
    }

    /**
     * 条目章节列表
     */
    @GetMapping("/subjects/{subjectId}/episodes")
    public Result<SubjectEpisodesVo> subjectEpisodes(
            @NotNull @PathVariable int subjectId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        SubjectEpisodesDto dto = bangumiClient.getSubjectEpisodes(subjectId, limit, offset);
        SubjectEpisodesVo vo = new SubjectEpisodesVo();
        BeanUtils.copyProperties(dto, vo);
        return Result.success(ResponseCode.SUCCESS, vo);
    }
}
