package com.ligg.module.statuenum;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Ligg
 * @create_time 2025/12/22 04:21
 * @update_time 2025/12/22 04:21
 **/

@Getter
@NoArgsConstructor
public enum Platform {
    ANDROID("android"),
    IOS("isos"),
    WEB("web"),
    LINUX("linux"),
    WINDOWS("windows"),
    MAC("mac");

    private String platform;
    Platform(String platform) {
        this.platform = platform;
    }
}
