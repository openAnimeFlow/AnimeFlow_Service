/**
 * @author Ligg
 * @date 2026/5/9 18:27
 */
package com.ligg.api.dandanplayapi;

import com.ligg.common.vo.DandanplayCommentVo;
import com.ligg.common.vo.DanmakuVo;

import java.util.List;

public interface DandanplayClient {

    /**
     * 获取弹弹play弹幕
     *
     * @param episodeId   弹幕库编号
     * @param withRelated 是否同时获取关联的第三方弹幕
     * @param chConvert   是否转换为中文
     * @return 弹幕列表
     */
    DandanplayCommentVo getDanmaku(int episodeId, Boolean withRelated, int chConvert);
}
