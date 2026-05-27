/**
 * @author Ligg
 * @date 2026/5/28 02:07
 */
package com.ligg.flowclient.controller.bangumi;

import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.common.thirdparty.CalendarDto;
import com.ligg.common.utils.Utils;
import com.ligg.common.vo.bangumi.CalendarVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
