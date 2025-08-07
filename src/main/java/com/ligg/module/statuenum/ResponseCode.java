package com.ligg.module.statuenum;

/**
 * @Author Ligg
 * @Time 2025/8/7
 **/

import lombok.Getter;

@Getter
public enum ResponseCode {
    SUCCESS(200, "成功"),
    /**
     * 资源不存在
     */
    NOT_FOUND(404, "资源不存在"),

    /**
     * 参数错误
     */
    PARAM_ERROR(400, "参数错误"),

    ERROR(500, "服务器错误");

    private final int code;
    private final String message;

    ResponseCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
