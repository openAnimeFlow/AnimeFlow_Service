/**
 * @author Ligg
 * @date 2026/6/24 22:35
 */
package com.ligg.flowclient.controller;

import com.ligg.common.entity.BackgroundEntity;
import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.flowclient.service.BackgroundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/common")
public class CommonController {

    private final BackgroundService backgroundService;

    /**
     * 获取背景图列表
     */
    @GetMapping("/background")
    public Result<List<BackgroundEntity>> getBackgroundList() {
        List<BackgroundEntity> list = backgroundService.getBackgroundList();
        return Result.success(ResponseCode.SUCCESS, list);
    }
}
