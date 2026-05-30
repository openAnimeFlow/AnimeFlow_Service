package com.ligg.api.bgmtvapi;

import com.ligg.common.exception.BangumiUpstreamException;
import com.ligg.common.vo.bangumi.BgmUserStatisticsVo;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析 bgm.tv 用户主页 HTML。
 * <p>
 * 从 {@code #userStats_all} 的第一个 {@code div} 中读取各统计项（每项含数值与名称两个 {@code span}）。
 */
@Component
public class BgmUserPageHtmlParser {

    public BgmUserStatisticsVo parseUserStatistics(String userPageHtml) {
        if (userPageHtml == null || userPageHtml.isBlank()) {
            throw new BangumiUpstreamException("用户主页内容为空");
        }

        Element container = Jsoup.parse(userPageHtml).getElementById("userStats_all");
        if (container == null) {
            throw new BangumiUpstreamException("用户主页未包含统计数据");
        }

        Element statsBlock = container.selectFirst("> div");
        if (statsBlock == null) {
            throw new BangumiUpstreamException("用户统计数据结构异常");
        }

        List<BgmUserStatisticsVo.StatisticVo> statistics = new ArrayList<>();
        for (Element row : statsBlock.children()) {
            if (!row.tagName().equalsIgnoreCase("div")) {
                continue;
            }
            String value = "";
            String name = "";
            List<Element> spans = row.select("> span");
            if (!spans.isEmpty()) {
                value = spans.get(0).text().trim();
            }
            if (spans.size() >= 2) {
                name = spans.get(1).text().trim();
            }
            statistics.add(new BgmUserStatisticsVo.StatisticVo(value, name));
        }

        if (statistics.isEmpty()) {
            throw new BangumiUpstreamException("用户统计数据为空");
        }

        return new BgmUserStatisticsVo(statistics);
    }
}
