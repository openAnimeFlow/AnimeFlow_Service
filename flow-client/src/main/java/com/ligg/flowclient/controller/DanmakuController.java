package com.ligg.flowclient.controller;

import com.ligg.api.dandanplayapi.DandanplayClient;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.common.vo.dandanplay.DandanplayBangumiDetailVo;
import com.ligg.common.vo.dandanplay.DandanplayCommentVo;
import com.ligg.common.vo.dandanplay.DandanplayEpisodeVo;
import com.ligg.common.vo.dandanplay.DandanplaySearchVo;
import com.ligg.common.response.Result;
import com.ligg.flowclient.service.DanmakuService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;



/**
 * 弹幕控制层
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/danmaku")
public class DanmakuController {

    private final DanmakuService danmakuService;

    private final DandanplayClient dandanplayClient;


    /**
     * 旧发送接口已弃用，仅提示客户端升级。
     */
    @Deprecated
    @PostMapping
    public Result<String> addDanmaku() {
        throw new IllegalArgumentException("当前发送弹幕接口已弃用，请更新客户端或等待新版本发布");
    }

    /**
     * 获取弹幕
     */
    @GetMapping("/{episodeId}")
    public Result<DandanplayCommentVo> getDanmaku(@PathVariable @NotNull(message = "episodeId 不能为空") Integer episodeId, @RequestParam(defaultValue = "false") Boolean withRelated, @RequestParam(defaultValue = "0") int chConvert) {

        return Result.success(ResponseCode.SUCCESS, danmakuService.getDanmaku(episodeId, withRelated, chConvert));
    }

    /**
     * 搜索番剧
     */
    @GetMapping("/search")
    public Result<DandanplaySearchVo> searchAnimes(@RequestParam String keyword,
                                                   @RequestParam(defaultValue = "1") Integer type) {
        return Result.success(ResponseCode.SUCCESS, dandanplayClient.searchAnimes(keyword, type));
    }

    @GetMapping("/search/episodes")
    public Result<DandanplaySearchVo> searchEpisodes(
            @RequestParam @NotNull @Size(min = 2, message = "anime 长度不能小于 2") String anime,
            @RequestParam(defaultValue = "true") Boolean v2) {
        return Result.success(ResponseCode.SUCCESS, dandanplayClient.searchEpisodes(anime, v2));
    }

    /**
     * 搜索番剧详情
     */
    @GetMapping("/bangumi/{bangumiId}")
    public Result<DandanplayBangumiDetailVo> getBangumiDetail(@PathVariable @NotNull(message = "bangumiId 不能为空") Integer bangumiId) {
        return Result.success(ResponseCode.SUCCESS, dandanplayClient.getBangumiDetail(bangumiId));
    }

    /**
     * 根据bangumiId获取番剧元素
     */
    @GetMapping("/bangumi/bgmtv/{bangumiId}")
    public Result<DandanplayEpisodeVo> getBangumiEpisode(@PathVariable @NotNull(message = "bangumiId 不能为空") Integer bangumiId) {
        return Result.success(ResponseCode.SUCCESS, dandanplayClient.getBangumiDetailByBangumiId(bangumiId));
    }
}
