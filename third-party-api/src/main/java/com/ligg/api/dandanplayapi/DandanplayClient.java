/**
 * @author Ligg
 * @date 2026/5/9 18:27
 */
package com.ligg.api.dandanplayapi;

import com.ligg.common.vo.dandanplay.DandanplayBangumiDetailVo;
import com.ligg.common.vo.dandanplay.DandanplayEpisodeVo;
import org.springframework.validation.annotation.Validated;

import com.ligg.common.vo.dandanplay.DandanplayCommentVo;
import com.ligg.common.vo.dandanplay.DandanplaySearchVo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Validated
public interface DandanplayClient {

    /**
     * 获取弹弹play弹幕
     *
     * @param episodeId   弹幕库编号
     * @param withRelated 是否同时获取关联的第三方弹幕
     * @param chConvert   是否转换为中文
     * @return 弹幕列表
     */
    DandanplayCommentVo getDanmaku(int episodeId, Boolean withRelated, int chConvert);

    /**
     * 弹弹play搜索番剧
     */
    DandanplaySearchVo searchAnimes(@NotNull @Size(min = 2, message = "keyword 长度不能小于 2") String keyword, Integer type);

    /**
     * 获取番剧元素
     */
    DandanplayBangumiDetailVo getBangumiDetail(@NotNull int bangumiId);

    /**
     * 获取番剧元素(根据bangumiId)
     */
    DandanplayEpisodeVo getBangumiDetailByBangumiId(@NotNull int bangumiId);
}
