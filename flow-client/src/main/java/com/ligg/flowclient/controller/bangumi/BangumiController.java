/**
 * @author Ligg
 * @date 2026/5/28 02:07
 */
package com.ligg.flowclient.controller.bangumi;

import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.common.thirdparty.CalendarDto;
import com.ligg.common.thirdparty.TrendingSubjectsDto;
import com.ligg.common.utils.Utils;
import com.ligg.common.vo.bangumi.CalendarVo;
import com.ligg.common.vo.bangumi.TrendingSubjectsVo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
