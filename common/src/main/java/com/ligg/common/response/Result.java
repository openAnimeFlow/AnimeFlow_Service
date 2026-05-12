package com.ligg.common.response;

import com.ligg.common.statuenum.ResponseCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author Ligg
 * @Time 2025/8/7
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private int code;
    private String message;
    private T data;


    public static <T> Result<T> success(ResponseCode status, T data) {
        return new Result<>(status.getCode(), status.getMessage(), data);
    }

    @SuppressWarnings("unchecked")
    public static <T> Result<T> success() {
        return new Result<>(ResponseCode.SUCCESS.getCode(), ResponseCode.SUCCESS.getMessage(), (T) "success");
    }

    @SuppressWarnings("unchecked")
    public static <T> Result<T> error(ResponseCode status) {
        return new Result<>(status.getCode(), status.getMessage(), (T) "error");
    }

    @SuppressWarnings("unchecked")
    public static <T> Result<T> error(ResponseCode status, String message) {
        return new Result<>(status.getCode(), message, (T) "error");
    }
}
