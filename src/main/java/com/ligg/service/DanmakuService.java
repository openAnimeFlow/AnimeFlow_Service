package com.ligg.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ligg.module.entity.DanmakuEntity;

public interface DanmakuService extends IService<DanmakuEntity> {

    /**
     * 保存弹幕数据
     * @param danmakuEntity 弹幕实体对象
     * @return 是否保存成功
     */
    boolean saveDanmaku(DanmakuEntity danmakuEntity);
}
