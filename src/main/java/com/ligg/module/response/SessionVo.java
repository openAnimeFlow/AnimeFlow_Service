package com.ligg.module.response;

import lombok.Data;

/**
 * @author Ligg
 * @create_time 2025/12/22 04:18
 * @update_time 2025/12/22 04:18
 **/
@Data
public class SessionVo {
    private String sessionId;
    private long expiresIn;
}
