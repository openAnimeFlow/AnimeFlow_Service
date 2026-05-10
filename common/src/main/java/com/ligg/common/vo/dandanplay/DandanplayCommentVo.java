package com.ligg.common.vo.dandanplay;

import java.util.List;

public record DandanplayCommentVo(Integer count, List<DanmakuVo> comments) {

    public record DanmakuVo(
            Long cid,
            String p,
            String m) {
    }

}
