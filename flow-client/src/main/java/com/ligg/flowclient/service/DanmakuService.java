package com.ligg.flowclient.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ligg.common.entity.DanmakuEntity;
import com.ligg.common.vo.dandanplay.DandanplayCommentVo;
import com.ligg.flowclient.module.dto.DanmakuDto;

import java.util.List;

public interface DanmakuService extends IService<DanmakuEntity> {

    /**
     * 保存弹幕数据
     *
     * @param danmakuDto 弹幕实体对象
     * @param bgmUserId
     * @return 是否保存成功
     */
    int saveDanmaku(DanmakuDto danmakuDto,int bgmUserId);

    /**
     * 查询本站弹幕（已拼好弹弹兼容的 {@code p} 等字段）。
     */
    List<DandanplayCommentVo.DanmakuVo> queryDanmaku(Integer episodeId);
}
