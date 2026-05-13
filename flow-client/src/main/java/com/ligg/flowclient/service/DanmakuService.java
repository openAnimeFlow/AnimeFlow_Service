package com.ligg.flowclient.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ligg.common.entity.DanmakuEntity;
import com.ligg.flowclient.module.dto.DanmakuDto;

public interface DanmakuService extends IService<DanmakuEntity> {

    /**
     * 保存弹幕数据
     *
     * @param danmakuDto 弹幕实体对象
     * @param bgmUserId
     * @return 是否保存成功
     */
    int saveDanmaku(DanmakuDto danmakuDto,int bgmUserId);


}
