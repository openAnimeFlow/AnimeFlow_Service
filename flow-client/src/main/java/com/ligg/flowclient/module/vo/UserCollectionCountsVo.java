package com.ligg.flowclient.module.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户 Bangumi 收藏各状态数量（本地 {@code user_bgm_collection} 统计）。
 * <p>type：1=想看 2=看过 3=在看 4=搁置 5=抛弃</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCollectionCountsVo {

    private int planToWatch;

    private int watched;

    private int watching;

    private int onHold;

    private int abandoned;
}
