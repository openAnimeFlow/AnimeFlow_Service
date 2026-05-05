package com.ligg.common.vo;

/**
 * Bangumi 用户详情（/v0/me 等接口返回结构）。
 *
 * @param id          用户 ID
 * @param username    用户名（可与 id 相同）
 * @param nickname    昵称
 * @param avatar      头像多尺寸 URL
 * @param sign        签名
 * @param group       用户组
 * @param joinedAt    注册时间（Unix 秒）
 * @param site        个人站点
 * @param location    位置
 * @param permissions 权限标记
 * @author Ligg
 */
public record BangumiUserinfoVO(
        Long id,
        String username,
        String nickname,
        Avatar avatar,
        String sign,
        Integer group,
        Long joinedAt,
        String site,
        String location,
        Permissions permissions
) {

    /**
     * 头像 small / medium / large 三档地址。
     */
    public record Avatar(String small, String medium, String large) {
    }

    /**
     * 用户权限位（随 Bangumi API 扩展可增加字段）。
     */
    public record Permissions(boolean subjectWikiEdit) {
    }
}
