package com.ligg.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ligg.mapper.DanmakuMapper;
import com.ligg.module.entity.DanmakuEntity;
import com.ligg.service.DanmakuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DanmakuServiceImpl extends ServiceImpl<DanmakuMapper,DanmakuEntity> implements DanmakuService  {

    @Autowired
    private DanmakuMapper danmakuMapper;

    /**
     * 保存弹幕数据
     * @param danmakuEntity 弹幕实体对象
     * @return 是否保存成功
     */
    @Override
    public boolean saveDanmaku(DanmakuEntity danmakuEntity) {
        danmakuEntity.setCreateTime(LocalDateTime.now());
        return danmakuMapper.insert(danmakuEntity) > 1;
    }
}
