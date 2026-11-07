package com.stravamirror.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    STRAVA_AUTH_ERROR(HttpStatus.UNAUTHORIZED),
    ACTIVITY_NOT_FOUND(HttpStatus.NOT_FOUND),
    STRAVA_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    STRAVA_SERVER_ERROR(HttpStatus.BAD_GATEWAY),
    STRAVA_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
