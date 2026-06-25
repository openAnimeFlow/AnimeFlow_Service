package com.ligg.common.handler;

import com.ligg.common.exception.BangumiUpstreamException;
import com.ligg.common.exception.CaptchaExpiredException;
import com.ligg.common.exception.EmailSendException;
import com.ligg.common.exception.LoginExpiredException;
import com.ligg.common.exception.AuthenticationFailedException;
import com.ligg.common.exception.AuthorizationException;
import com.ligg.common.exception.UpdateRateLimitException;
import com.ligg.common.exception.RateLimitExceededException;
import com.ligg.common.exception.VerificationCodeException;
import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 主键或唯一键冲突
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.error("主键/唯一键冲突: {}", e.getMessage());
        return Result.error(ResponseCode.PARAM_ERROR, "数据已存在，请勿重复提交");
    }

    /**
     * 数据完整性违规（外键约束、非空、长度超限等）
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.error("数据完整性违规: {}", e.getMessage());
        return Result.error(ResponseCode.PARAM_ERROR, "数据格式不合法");
    }

    /**
     * 请求参数校验失败 @Valid 用于 @RequestBody
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        log.warn("参数校验失败: {}", msg);
        return Result.error(ResponseCode.PARAM_ERROR, msg);
    }

    /**
     * 表单参数校验失败
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数绑定失败";
        log.warn("参数绑定失败: {}", msg);
        return Result.error(ResponseCode.PARAM_ERROR, msg);
    }

    /**
     * 方法参数校验失败
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", msg);
        return Result.error(ResponseCode.PARAM_ERROR, msg);
    }

    /**
     * 缺少必填请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return Result.error(ResponseCode.PARAM_ERROR, "缺少必填参数: " + e.getParameterName());
    }

    /**
     * 参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}", e.getMessage());
        return Result.error(ResponseCode.PARAM_ERROR, "参数类型错误: " + e.getName());
    }

    /**
     * 请求体格式错误（例如 JSON 解析失败）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(ResponseCode.PARAM_ERROR, "请求体格式错误");
    }

    /**
     * 非法参数
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return Result.error(ResponseCode.PARAM_ERROR, e.getMessage());
    }

    /**
     * 缺少 Authorization 请求头（论坛评论等拦截器）
     */
    @ExceptionHandler(AuthorizationException.class)
    public Result<Void> handleMissingAuthorization(AuthorizationException e) {
        log.warn("未授权: {}", e.getMessage());
        return Result.error(ResponseCode.UNAUTHORIZED, e.getMessage());
    }

    /**
     * 授权登录失败
     */
    @ExceptionHandler(AuthenticationFailedException.class)
    public Result<Void> handleAuthenticationFailed(AuthenticationFailedException e) {
        log.warn(e.getMessage());
        return Result.error(ResponseCode.UNAUTHORIZED, e.getMessage());
    }

    /**
     * Bangumi 返回 401，访问令牌失效或登录已过期
     */
    @ExceptionHandler(LoginExpiredException.class)
    public Result<Void> handleBangumiLoginExpired(LoginExpiredException e) {
        log.warn("Bangumi 登录过期: {}", e.getMessage());
        return Result.error(ResponseCode.UNAUTHORIZED, "登录已过期，请重新登录");
    }

    /**
     * Bangumi Next API 超时或网络异常等（非 401）
     */
    @ExceptionHandler(BangumiUpstreamException.class)
    public Result<Void> handleBangumiUpstream(BangumiUpstreamException e) {
        log.warn(e.getMessage());
        return Result.error(ResponseCode.ERROR, e.getMessage());
    }

    /**
     * 邮件发送失败
     */
    @ExceptionHandler(EmailSendException.class)
    public Result<Void> handleEmailSend(EmailSendException e) {
        log.warn("邮件发送失败: {}", e.getMessage(), e);
        return Result.error(ResponseCode.ERROR, e.getMessage());
    }

    /**
     * 接口限流（如按 IP 的 Redis Lua 限流）
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Result<Void>> handleRateLimitExceeded(RateLimitExceededException e) {
        log.warn("请求限流: {}", e.getMessage() != null ? e.getMessage() : ResponseCode.TOO_MANY_REQUESTS.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Result.error(ResponseCode.TOO_MANY_REQUESTS, ResponseCode.TOO_MANY_REQUESTS.getMessage()));
    }

    /**
     * 更新操作频率限制（正常 200，携带业务错误码）
     */
    @ExceptionHandler(UpdateRateLimitException.class)
    public Result<Void> handleUpdateRateLimit(UpdateRateLimitException e) {
        log.warn("更新操作频率限制: {}", e.getMessage());
        return Result.error(ResponseCode.TOO_MANY_REQUESTS, e.getMessage());
    }

    /**
     * 验证码过期
     */
    @ExceptionHandler(CaptchaExpiredException.class)
    public Result<Void> handleCaptchaExpired(CaptchaExpiredException e) {
        log.warn("验证码已过期: {}", e.getMessage());
        return Result.error(ResponseCode.PARAM_ERROR, e.getMessage());
    }

    /**
     * 验证码错误
     */
    @ExceptionHandler(VerificationCodeException.class)
    public Result<Void> handleVerificationCode(VerificationCodeException e) {
        log.warn("验证码错误: {}", e.getMessage());
        return Result.error(ResponseCode.PARAM_ERROR, e.getMessage());
    }

    /**
     * 兜底异常处理
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return Result.error(ResponseCode.ERROR, "服务器繁忙，请稍后再试");
    }
}
