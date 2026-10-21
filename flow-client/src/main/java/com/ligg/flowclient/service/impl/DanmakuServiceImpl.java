package com.ligg.flowclient.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ligg.api.dandanplayapi.DandanplayClient;
import com.ligg.common.entity.BangumiEpisodeEntity;
import com.ligg.common.vo.dandanplay.DandanplayDetailVo;
import com.ligg.common.vo.dandanplay.DandanplayEpisodeVo;
import com.ligg.common.vo.dandanplay.DandanplayCommentVo;
import com.ligg.flowclient.mapper.BangumiEpisodeMapper;
import com.ligg.flowclient.mapper.DanmakuMapper;
import com.ligg.common.entity.DanmakuEntity;
import com.ligg.flowclient.module.dto.DanmakuDto;
import com.ligg.flowclient.service.DanmakuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class DanmakuServiceImpl extends ServiceImpl<DanmakuMapper, DanmakuEntity> implements DanmakuService {

    private final DanmakuMapper danmakuMapper;
    private final BangumiEpisodeMapper episodeMapper;
    private final DandanplayClient dandanplayClient;

    @Override
    public @NonNull DandanplayCommentVo getDandanplayDanmaku(int subjectId, int episodeId,
                                                            @NonNull Boolean withRelated, int chConvert) {
        Integer episodeNumber = episodeMapper.selectEpisodeNumber(subjectId, episodeId);
        if (episodeNumber == null || episodeNumber <= 0) {
            throw new IllegalArgumentException("剧集不存在、不属于当前条目或不是正片");
        }
        DandanplayEpisodeVo detail = dandanplayClient.getBangumiDetailByBangumiId(subjectId);
        if (detail == null || !detail.success() || detail.bangumi() == null
                || detail.bangumi().episodes() == null) {
            return new DandanplayCommentVo(0, List.of());
        }
        List<Integer> matches = detail.bangumi().episodes().stream()
                .filter(episode -> episode != null && episode.episodeId() > 0
                        && matchesEpisodeNumber(episode, episodeNumber))
                .map(DandanplayDetailVo.EpisodeVo::episodeId)
                .distinct()
                .toList();
        if (matches.size() != 1) {
            log.info("无法唯一匹配弹弹play正片, subjectId={}, episodeId={}, episodeNumber={}, matches={}",
                    subjectId, episodeId, episodeNumber, matches.size());
            return new DandanplayCommentVo(0, List.of());
        }
        DandanplayCommentVo result = dandanplayClient.getDanmaku(matches.getFirst(), withRelated, chConvert);
        return result != null ? result : new DandanplayCommentVo(0, List.of());
    }

    private boolean matchesEpisodeNumber(DandanplayDetailVo.EpisodeVo episode, int episodeNumber) {
        if (episode.episodeNumber() == null) return false;
        try {
            return new BigDecimal(episode.episodeNumber().trim()).compareTo(BigDecimal.valueOf(episodeNumber)) == 0;
        } catch (NumberFormatException e) {
            // SP、OP、预告等非数字编号不参与正片匹配。
            return false;
        }
    }

    @Override
    public DandanplayCommentVo getDanmaku(int episodeId, Boolean withRelated, int chConvert) {
        DandanplayCommentVo upstream = dandanplayClient.getDanmaku(episodeId, withRelated, chConvert);
        List<DandanplayCommentVo.DanmakuVo> merged = new ArrayList<>(
                upstream != null && upstream.comments() != null ? upstream.comments() : List.of());
        return new DandanplayCommentVo(merged.size(), merged);
    }

    @Override
    public void saveLocalDanmaku(DanmakuDto dto, long userId) {
        BangumiEpisodeEntity episode = episodeMapper.selectById(dto.getEpisodeId());
        if (episode == null || !dto.getSubjectId().equals(episode.getSubjectId())) {
            throw new IllegalArgumentException("剧集不存在或不属于当前条目");
        }
        DanmakuEntity entity = new DanmakuEntity();
        BeanUtils.copyProperties(dto, entity);
        entity.setUserId(userId);
        entity.setSource("[AnimeFlow]");
        entity.setCreateTime(LocalDateTime.now());
        danmakuMapper.insert(entity);
    }

    @Override
    public @NonNull List<DandanplayCommentVo.DanmakuVo> getLocalDanmaku(int subjectId, int episodeId) {
        return danmakuMapper.selectList(new LambdaQueryWrapper<DanmakuEntity>()
                        .eq(DanmakuEntity::getEpisodeId, episodeId)
                        .eq(DanmakuEntity::getSubjectId, subjectId))
                .stream()
                .map(DanmakuServiceImpl::toAnimeFlowItem)
                .toList();
    }

    private static DandanplayCommentVo.DanmakuVo toAnimeFlowItem(DanmakuEntity e) {
        return new DandanplayCommentVo.DanmakuVo(
                e.getId(),
                buildP(e),
                e.getComment() != null ? e.getComment() : ""
        );
    }

    /**
     * 与弹弹前四段一致：「时间(秒),类型,颜色(十进制),平台」；本站额外追加第五段 Flow {@code userId}（无则 0）。平台取 {@link DanmakuEntity#getSource()}，逗号转空格。
     */
    private static String buildP(DanmakuEntity e) {
        double timeSec = e.getTime() != null ? e.getTime() : 0.0;
        int type = e.getType() != null ? e.getType() : 1;
        int color = e.getColor() != null ? e.getColor() : 0xFFFFFF;
        String platform = (e.getSource() != null && !e.getSource().isBlank())
                ? e.getSource().replace(",", " ")
                : "AnimeFlow";
        long userId = e.getUserId() != null ? e.getUserId() : 0L;
        return String.format(Locale.ROOT, "%.3f,%d,%d,%s,%d", timeSec, type, color, platform, userId);
    }
}
