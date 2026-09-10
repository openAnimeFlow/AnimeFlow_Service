package com.ligg.common.response;

import com.ligg.common.statuenum.ResponseCode;
import com.fasterxml.jackson.annotation.JsonInclude;
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

    /** Machine-readable auth failure, additive to the legacy code=401 contract. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String authReason;

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> authError(String reason, String message) {
        Result<T> result = error(ResponseCode.UNAUTHORIZED, message);
        result.setAuthReason(reason);
        return result;
    }


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
