package com.ligg.flowclient.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ligg.common.entity.DanmakuEntity;
import com.ligg.common.vo.dandanplay.DandanplayCommentVo;
import com.ligg.flowclient.module.dto.DanmakuDto;
import org.jspecify.annotations.NonNull;

import java.util.List;

public interface DanmakuService extends IService<DanmakuEntity> {

    /**
     * 保存以 Bangumi 条目和章节定位的本站弹幕，userId 由服务端验证 Flow JWT 得到。
     */
    void saveLocalDanmaku(DanmakuDto danmakuDto, long userId);

    /** 按章节 ID 查询全部本站弹幕，排序由客户端处理。 */
    @NonNull List<DandanplayCommentVo.DanmakuVo> getLocalDanmaku(int subjectId,int episodeId);

    /** 按弹弹play剧集 ID 获取上游与本站合并弹幕。 */
    DandanplayCommentVo getDanmaku(int episodeId, Boolean withRelated, int chConvert);

    /** 按已由 Controller 校验的 Bangumi 条目和章节 ID 获取弹弹play弹幕，结果始终非空。 */
    @NonNull DandanplayCommentVo getDandanplayDanmaku(int subjectId, int episodeId,
                                                    @NonNull Boolean withRelated, int chConvert);
}
