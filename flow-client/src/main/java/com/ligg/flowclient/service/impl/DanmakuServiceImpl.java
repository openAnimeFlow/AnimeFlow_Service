package com.ligg.flowclient.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ligg.flowclient.mapper.DanmakuMapper;
import com.ligg.common.entity.DanmakuEntity;
import com.ligg.flowclient.module.dto.DanmakuDto;
import com.ligg.flowclient.service.DanmakuService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DanmakuServiceImpl extends ServiceImpl<DanmakuMapper,DanmakuEntity> implements DanmakuService  {

    private final DanmakuMapper danmakuMapper;

    /**
     * 保存弹幕数据
     *
     * @param danmakuDto 弹幕实体对象
     * @param bgmUserId
     * @return 是否保存成功
     */
    @Override
    public int saveDanmaku(DanmakuDto danmakuDto, int bgmUserId) {

        DanmakuEntity danmakuEntity = new DanmakuEntity();
        BeanUtils.copyProperties(danmakuDto, danmakuEntity);
        danmakuEntity.setCreateTime(LocalDateTime.now());
        danmakuEntity.setBgmUserId(bgmUserId);
        // 目前默认直接设置来源为AnimeFlow，后续可以根据实际情况进行调整
        danmakuEntity.setSource("AnimeFlow");
        return danmakuMapper.insert(danmakuEntity);
    }
}
