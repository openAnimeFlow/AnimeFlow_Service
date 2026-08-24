package com.ligg.flowclient.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.model.CoverImages;
import com.ligg.common.utils.BangumiSeasonUtils;
import com.ligg.common.vo.bangumi.SeasonCalendarVo;
import com.ligg.flowclient.mapper.BangumiEpisodeMapper;
import com.ligg.flowclient.mapper.BangumiSubjectMapper;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.module.dto.SeasonEpisodeRow;
import com.ligg.flowclient.module.dto.SeasonSubjectRow;
import com.ligg.flowclient.service.ImageBackfillService;
import com.ligg.flowclient.service.UserEpisodeWatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BangumiSeasonCalendarServiceTest {

    private static final LocalDate FIXED_NOW = LocalDate.of(2026, 7, 15);

    @org.mockito.Mock
    private BangumiEpisodeMapper episodeMapper;

    @org.mockito.Mock
    private BangumiSubjectMapper subjectMapper;

    @org.mockito.Mock
    private ImageBackfillService imageBackfillService;

    @org.mockito.Mock
    private UserBgmCollectionMapper userBgmCollectionMapper;

    @org.mockito.Mock
    private UserEpisodeWatchService userEpisodeWatchService;

    @Test
    void groupsByWeekdayAndFallsBackToStartDate() {
        SeasonSubjectRow thursday = row(1, "2026-07-02", "星期四", 8.0);
        thursday.setFavorite("{\"done\": 12}");
        SeasonSubjectRow saturday = row(2, "2026-07-04", "星期六", 7.0);
        SeasonSubjectRow unknownWeekday = row(3, "2026-07-04", "", 6.0);
        when(subjectMapper.selectSeasonSubjects(
                eq(BangumiSeasonUtils.currentSeasonMonthPrefix(FIXED_NOW)), eq(2), eq(false)))
                .thenReturn(List.of(thursday, saturday, unknownWeekday));
        LocalDate today = FIXED_NOW;
        when(subjectMapper.selectSeasonEpisodes(eq(BangumiSeasonUtils.currentSeasonMonthPrefix(FIXED_NOW))))
                .thenReturn(List.of(
                        episode(1, 1, today.minusDays(7).toString()),
                        episode(1, 2, today.plusDays(7).toString())));
        mockImage();

        BangumiServiceImpl service = service();
        SeasonCalendarVo vo = service.getSeasonCalendar(false, FIXED_NOW);

        assertEquals(2026, vo.getYear());
        assertEquals(7, vo.getMonth());
        assertEquals("2026年7月新番",
                vo.getSeasonName());
        assertEquals(3, vo.getTotal());
        assertEquals(1, vo.getDays().get("4").size());
        assertEquals(2, vo.getDays().get("6").size());
        assertNull(vo.getUnknown());
        assertEquals(12, vo.getDays().get("4").get(0).getWatchers());
        assertEquals(8.0, vo.getDays().get("4").get(0).getSubject().getRating().getScore());
        assertEquals(List.of("TV", "日本"), vo.getDays().get("4").get(0).getSubject().getMetaTags());
        assertEquals(2, vo.getDays().get("4").get(0).getEpisodeCount());
        assertEquals(1, vo.getDays().get("4").get(0).getLatestEpisode().getSort());
        assertEquals(today.minusDays(7).toString(),
                vo.getDays().get("4").get(0).getLatestEpisode().getAirdate());
        assertEquals(2, vo.getDays().get("4").get(0).getNextEpisode().getSort());
    }

    @Test
    void multipleWeekdaysPutSubjectIntoEachDay() {
        SeasonSubjectRow row = row(4, "2026-07-03", "星期二、星期五", 7.5);
        when(subjectMapper.selectSeasonSubjects(
                eq(BangumiSeasonUtils.currentSeasonMonthPrefix(FIXED_NOW)), eq(2), eq(false)))
                .thenReturn(List.of(row));
        mockImage();

        SeasonCalendarVo vo = service().getSeasonCalendar(false, FIXED_NOW);

        assertEquals(1, vo.getDays().get("2").size());
        assertEquals(1, vo.getDays().get("5").size());
        assertEquals(4, vo.getDays().get("2").get(0).getSubject().getId());
        assertEquals(4, vo.getDays().get("5").get(0).getSubject().getId());
    }

    private BangumiServiceImpl service() {
        return new BangumiServiceImpl(
                episodeMapper,
                subjectMapper,
                new ObjectMapper(),
                imageBackfillService,
                userBgmCollectionMapper,
                userEpisodeWatchService);
    }

    private static SeasonSubjectRow row(int id, String date, String weekday, double score) {
        SeasonSubjectRow row = new SeasonSubjectRow();
        row.setId(id);
        row.setType(2);
        row.setName("测试番剧" + id);
        row.setNameCn("测试番剧" + id);
        row.setInfobox("{{Infobox animanga/TVAnime\n|放送星期= " + weekday + "\n}}");
        row.setPlatform(1);
        row.setNsfw(false);
        row.setScore(score);
        row.setDate(date);
        row.setMetaTags("[\"TV\", \"日本\"]");
        return row;
    }

    private static SeasonEpisodeRow episode(int subjectId, int sort, String airdate) {
        SeasonEpisodeRow episode = new SeasonEpisodeRow();
        episode.setSubjectId(subjectId);
        episode.setId(subjectId * 100 + sort);
        episode.setName("第" + sort + "话");
        episode.setSort(sort);
        episode.setAirdate(airdate);
        return episode;
    }

    private void mockImage() {
        CoverImages images = new CoverImages();
        images.setLarge("https://example.com/cover.jpg");
        when(imageBackfillService.resolve(any(), anyInt(), isNull())).thenReturn(images);
    }
}
