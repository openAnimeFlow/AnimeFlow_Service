package com.ligg.common.exception;

/**
 * 邮件发送失败（如 Resend API 调用异常）。
 */
public class EmailSendException extends RuntimeException {

    public EmailSendException(String message) {
        super(message);
    }

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
