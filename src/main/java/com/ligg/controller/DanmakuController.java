package com.ligg.controller;

import com.ligg.module.dto.DanmakuDto;
import com.ligg.module.entity.DanmakuEntity;
import com.ligg.module.response.Result;
import com.ligg.service.DanmakuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 弹幕控制层
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/danmaku")
public class DanmakuController {

    @Autowired
    private DanmakuService danmakuService;

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
}
