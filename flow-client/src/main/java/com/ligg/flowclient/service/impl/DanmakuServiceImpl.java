package com.ligg.flowclient.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ligg.flowclient.mapper.DanmakuMapper;
import com.ligg.common.entity.DanmakuEntity;
import com.ligg.common.vo.AnimeFlowDanmakuItemVo;
import com.ligg.flowclient.module.dto.DanmakuDto;
import com.ligg.flowclient.service.DanmakuService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DanmakuServiceImpl extends ServiceImpl<DanmakuMapper, DanmakuEntity> implements DanmakuService {

    private final DanmakuMapper danmakuMapper;

    /**
     * 保存弹幕数据
     *
     * @param danmakuDto 弹幕实体对象
     * @param bgmUserId 用户ID
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

    @Override
    public List<AnimeFlowDanmakuItemVo> queryDanmaku(Integer episodeId) {
        return danmakuMapper.selectList(new LambdaQueryWrapper<DanmakuEntity>().eq(DanmakuEntity::getEpisodeId, episodeId))
                .stream()
                .map(DanmakuServiceImpl::toAnimeFlowItem)
                .toList();
    }

    private static AnimeFlowDanmakuItemVo toAnimeFlowItem(DanmakuEntity e) {
        return new AnimeFlowDanmakuItemVo(
                e.getId(),
                buildP(e),
                e.getComment() != null ? e.getComment() : "",
                e.getBgmUserId() != null ? Integer.toString(e.getBgmUserId()) : null
        );
    }

    /**
     * 与弹弹一致：「出现时间(秒),类型,颜色(十进制),平台」；平台取 {@link DanmakuEntity#getSource()}，逗号转空格。
     */
    private static String buildP(DanmakuEntity e) {
        double timeSec = e.getTime() != null ? e.getTime() : 0.0;
        int type = e.getType() != null ? e.getType() : 1;
        int color = e.getColor() != null ? e.getColor() : 0xFFFFFF;
        String platform = (e.getSource() != null && !e.getSource().isBlank())
                ? e.getSource().replace(",", " ")
                : "AnimeFlow";
        return String.format(Locale.ROOT, "%.3f,%d,%d,%s", timeSec, type, color, platform);
    }
}
