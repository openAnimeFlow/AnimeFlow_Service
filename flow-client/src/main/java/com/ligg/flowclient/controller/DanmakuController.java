package com.ligg.flowclient.controller;

import com.ligg.api.dandanplayapi.DandanplayClient;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.common.vo.dandanplay.BangumiDetailVo;
import com.ligg.common.vo.dandanplay.DandanplayCommentVo;
import com.ligg.common.vo.dandanplay.DanmakuEpisodeVo;
import com.ligg.common.vo.dandanplay.DanmakuSearchVo;
import com.ligg.flowclient.module.dto.DanmakuDto;
import com.ligg.flowclient.module.entity.DanmakuEntity;
import com.ligg.common.response.Result;
import com.ligg.flowclient.service.DanmakuService;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


/**
 * 弹幕控制层
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/danmaku")
public class DanmakuController {

    @Autowired
    private DanmakuService danmakuService;

    @Autowired
    private DandanplayClient dandanplayClient;

    /**
     * 添加弹幕
     */
    @PostMapping
    public Result<String> addDanmaku(DanmakuDto danmakuDto) {
        DanmakuEntity danmakuEntity = new DanmakuEntity();
        BeanUtils.copyProperties(danmakuDto, danmakuEntity);
        danmakuService.saveDanmaku(danmakuEntity);
        return Result.success();
    }

    /**
     * 获取弹幕
     */
    @GetMapping("/{episodeId}")
    public Result<DandanplayCommentVo> getDanmaku(@PathVariable @NotNull(message = "episodeId 不能为空") Integer episodeId,
                                                  @RequestParam(defaultValue = "false") Boolean withRelated,
                                                  @RequestParam(defaultValue = "0") int chConvert) {

        DandanplayCommentVo danmakuVoList = dandanplayClient.getDanmaku(episodeId, withRelated, chConvert);
        //TODO 后续添加自己服务的弹幕信息
        return Result.success(ResponseCode.SUCCESS, danmakuVoList);
    }

    /**
     * 搜索番剧
     */
    @GetMapping("/search")
    public Result<DanmakuSearchVo> searchAnimes(@RequestParam String keyword,
                                                @RequestParam(defaultValue = "1") Integer type) {
        return Result.success(ResponseCode.SUCCESS, dandanplayClient.searchAnimes(keyword, type));
    }

    /**
     * 搜索番剧详情
     */
    @GetMapping("/bangumi/{bangumiId}")
    public Result<BangumiDetailVo> getBangumiDetail(@PathVariable @NotNull(message = "bangumiId 不能为空") Integer bangumiId) {
        return Result.success(ResponseCode.SUCCESS, dandanplayClient.getBangumiDetail(bangumiId));
    }

    /**
     * 根据bangumiId获取番剧元素
     */
    @GetMapping("/bangumi/bgmtv/{bangumiId}")
    public Result<DanmakuEpisodeVo> getBangumiEpisode(@PathVariable @NotNull(message = "bangumiId 不能为空") Integer bangumiId) {
        return Result.success(ResponseCode.SUCCESS, dandanplayClient.getBangumiDetailByBangumiId(bangumiId));
    }
}
