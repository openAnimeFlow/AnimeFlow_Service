package com.ligg.common.vo;

/**
 * AnimeFlow 自有弹幕单条结构（与弹弹 {@link com.ligg.common.vo.dandanplay.DandanplayCommentVo.DanmakuVo} 字段对齐，便于合并返回）。
 *
 * @param cid       数据库主键，对应合并列表中的 cid
 * @param p         出现时间(秒)、类型、颜色(十进制)、平台 逗号分隔，与弹弹 {@code p} 约定一致
 * @param m         弹幕正文
 * @param bgmUserId Bangumi 用户 id 字符串，无则 null
 */
public record AnimeFlowDanmakuItemVo(Long cid, String p, String m, String bgmUserId) {
}
