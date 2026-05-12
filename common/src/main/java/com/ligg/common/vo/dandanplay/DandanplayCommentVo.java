package com.ligg.common.vo.dandanplay;

import java.util.List;

public record DandanplayCommentVo(Integer count, List<DanmakuVo> comments) {

    public record DanmakuVo(
            Long cid,
            /*
             * 出现时间,弹幕类型,颜色,平台
             */
            String p,
            String m) {
    }

}
