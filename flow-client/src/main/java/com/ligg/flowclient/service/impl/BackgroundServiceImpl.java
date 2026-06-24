package com.ligg.flowclient.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ligg.common.entity.BackgroundEntity;
import com.ligg.flowclient.mapper.BackgroundMapper;
import com.ligg.flowclient.service.BackgroundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BackgroundServiceImpl extends ServiceImpl<BackgroundMapper, BackgroundEntity> implements BackgroundService {

    @Override
    public List<BackgroundEntity> getBackgroundList() {
        return lambdaQuery()
                .orderByAsc(BackgroundEntity::getId)
                .list();
    }
}
