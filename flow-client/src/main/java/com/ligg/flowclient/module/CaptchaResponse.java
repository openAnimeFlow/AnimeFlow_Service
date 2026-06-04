/**
 * @author Ligg
 * @date 2026/6/5 01:25
 */
package com.ligg.flowclient.module;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CaptchaResponse {

    /**
     * 验证码id
     */
    private String captchaId;

    /**
     * 验证码图片
     */
    private String imageBase64;
}
