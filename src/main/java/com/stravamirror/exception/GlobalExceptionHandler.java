package com.stravamirror.exception;

import com.stravamirror.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        ErrorResponse body = new ErrorResponse(
                ErrorCode.VALIDATION_ERROR.name(),
                e.getMessage());
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        ErrorResponse body = new ErrorResponse(
                ErrorCode.VALIDATION_ERROR.name(),
                e.getMessage());
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        ErrorResponse body = new ErrorResponse(
                ErrorCode.VALIDATION_ERROR.name(),
                "Invalid parameter value: " + e.getName());
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(body);
    }

    @ExceptionHandler(StravaApiException.class)
    public ResponseEntity<ErrorResponse> handleStravaApi(StravaApiException e) {
        ErrorCode code = e.getErrorCode();
        ErrorResponse body = new ErrorResponse(code.name(), e.getMessage());
        return ResponseEntity
                .status(code.getHttpStatus())
                .body(body);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException e) {
        ErrorResponse body = new ErrorResponse(
                ErrorCode.DATABASE_ERROR.name(),
                "Database error");
        return ResponseEntity
                .status(ErrorCode.DATABASE_ERROR.getHttpStatus())
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        ErrorResponse body = new ErrorResponse(
                ErrorCode.DATABASE_ERROR.name(),
                "An unexpected error occurred");
        return ResponseEntity
                .status(ErrorCode.DATABASE_ERROR.getHttpStatus())
                .body(body);
    }
}
