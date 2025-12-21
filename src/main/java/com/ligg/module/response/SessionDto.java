package com.ligg.module.response;

import com.ligg.module.statuenum.Platform;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Ligg
 * @create_time 2025/12/22 04:33
 * @update_time 2025/12/22 04:33
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionDto {
    private String sessionId;
    private Integer expiresIn;
    private Platform platform;
}
