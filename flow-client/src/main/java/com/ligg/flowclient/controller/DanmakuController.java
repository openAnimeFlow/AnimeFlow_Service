package com.ligg.flowclient.controller;

import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.api.dandanplayapi.DandanplayClient;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.common.vo.BangumiUserinfoVO;
import com.ligg.common.vo.dandanplay.DandanplayBangumiDetailVo;
import com.ligg.common.vo.dandanplay.DandanplayCommentVo;
import com.ligg.common.vo.dandanplay.DandanplayEpisodeVo;
import com.ligg.common.vo.dandanplay.DandanplaySearchVo;
import com.ligg.flowclient.annotation.DanmakuSendRateLimit;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.module.dto.DanmakuDto;
import com.ligg.common.response.Result;
import com.ligg.flowclient.service.DanmakuService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
    private final BangumiClient bangumiClient;


    /**
     * 添加弹幕
     */
    @PostMapping
    @DanmakuSendRateLimit
    public Result<String> addDanmaku(@Valid DanmakuDto danmakuDto,
                                     @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken
    ) {
        BangumiUserinfoVO bgmUserInfo = bangumiClient.getMe(accessToken);
        danmakuService.saveDanmaku(danmakuDto, bgmUserInfo.id());
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
    public Result<DandanplaySearchVo> searchAnimes(@RequestParam String keyword,
                                                   @RequestParam(defaultValue = "1") Integer type) {
        return Result.success(ResponseCode.SUCCESS, dandanplayClient.searchAnimes(keyword, type));
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
