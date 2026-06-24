package com.ligg.flowclient.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ligg.common.entity.BackgroundEntity;

import java.util.List;

public interface BackgroundService extends IService<BackgroundEntity> {

    /**
     * 获取所有背景图片列表
     */
    List<BackgroundEntity> getBackgroundList();
}
