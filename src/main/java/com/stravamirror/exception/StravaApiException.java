package com.stravamirror.exception;

public class StravaApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public StravaApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public StravaApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
