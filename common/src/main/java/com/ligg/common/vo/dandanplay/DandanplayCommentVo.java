package com.ligg.common.vo.dandanplay;

import java.util.List;

public record DandanplayCommentVo(Integer count, List<DanmakuVo> comments) {

    public record DanmakuVo(
            Long cid,
            /*
             * 出现时间,弹幕类型,颜色,平台
             */
            String p,
            String m,
            /* 本站关联的 Bangumi 用户 id；第三方无此字段时为 null */
            String bgmUserId) {
    }

}
