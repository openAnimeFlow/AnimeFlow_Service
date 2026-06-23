/**
 * @author Ligg
 * @date 2026/6/8 09:49
 */
package com.ligg.flowclient.module.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FlowUserVo {
    private Long id;
    private String email;
    private String nickname;
    private String avatar;
    private Long createTime;

    /** 本地 Bangumi 收藏各状态数量 */
    private UserCollectionCountsVo collectionCounts;
}
