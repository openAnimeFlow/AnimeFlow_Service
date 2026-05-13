/**
 * 弹弹 play 番剧详情（含剧集列表）接口响应，与 {@link BangumiDetailVo} 结构一致。
 *
 * @author Ligg
 * @date 2026/5/10 22:48
 */
package com.ligg.common.vo.dandanplay;

public record DandanplayEpisodeVo(
        DandanplayDetailVo bangumi,
        int errorCode,
        boolean success,
        String errorMessage) {
}
