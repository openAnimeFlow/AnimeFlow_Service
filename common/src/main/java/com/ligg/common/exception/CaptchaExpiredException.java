package com.ligg.common.exception;

public class CaptchaExpiredException extends RuntimeException {

  public CaptchaExpiredException(String message) {
    super(message);
  }

  public CaptchaExpiredException(String message, Throwable cause) {
    super(message, cause);
  }
}
