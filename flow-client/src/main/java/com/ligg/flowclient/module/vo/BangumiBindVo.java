package com.ligg.flowclient.module.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BangumiBindVo {

    private boolean bound;

    private Long platformUid;

    private String username;

    private String nickname;

    private String avatar;

    public static BangumiBindVo notBound() {
        return new BangumiBindVo(false, null, null, null, null);
    }
}
