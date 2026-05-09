/**
 * @author Ligg
 * @date 2026/5/9 18:27
 */
package com.ligg.api.dandanplayapi;

import com.ligg.common.vo.DanmakuVo;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

public class DandanplayClientImpl implements DandanplayClient {



    @Override
    public List<DanmakuVo> getDanmaku(Long episodeId, Boolean withRelated, int chConvert) {
        return List.of();
    }
}
