/**
 * @author Ligg
 * @date 2026/5/5 10:59
 */
package com.ligg.api.bangumiapi;

import com.ligg.common.vo.BangumiUserinfoVO;

public interface BangumiClient {
    /**
     * 获取当前用户信息
     */
    BangumiUserinfoVO getMe(String accessToken);
}
