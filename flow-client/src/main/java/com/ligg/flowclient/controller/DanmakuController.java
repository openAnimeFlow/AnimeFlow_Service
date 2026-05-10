package com.ligg.flowclient.controller;

import com.ligg.api.dandanplayapi.DandanplayClient;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.common.vo.DandanplayCommentVo;
import com.ligg.flowclient.module.dto.DanmakuDto;
import com.ligg.flowclient.module.entity.DanmakuEntity;
import com.ligg.common.response.Result;
import com.ligg.flowclient.service.DanmakuService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * 弹幕控制层
 */
@Slf4j
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
    @GetMapping
    public Result<DandanplayCommentVo> getDanmaku(@NonNull Integer episodeId,
                                                  @RequestParam(defaultValue = "false") Boolean withRelated,
                                                  @RequestParam(defaultValue = "0") int chConvert) {

        DandanplayCommentVo danmakuVoList = dandanplayClient.getDanmaku(episodeId, withRelated, chConvert);
        //TODO 后续添加自己服务的弹幕信息
        return Result.success(ResponseCode.SUCCESS, danmakuVoList);
    }
}
