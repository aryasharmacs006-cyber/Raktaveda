package com.raktaveda.common;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Turns every error into { "error", "message", "timestamp" }. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleBadInput(Exception ex) {
        // The exception text can contain what the user typed, so it is not copied anywhere.
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "error.validation.failed");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(Exception ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "error.method_not_allowed");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaType(Exception ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "error.unsupported_media_type");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(Exception ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "error.not_found");
    }

    // Method-level security (@PreAuthorize) throws these from inside controllers.
    // Without these two handlers the catch-all below would turn them into a 500.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(Exception ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "error.forbidden");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(Exception ex) {
        return build(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "error.auth.required");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        // Log the type and first stack frame only. Database error texts can contain
        // values (phone hashes, emails), so the message is never logged.
        StackTraceElement[] trace = ex.getStackTrace();
        log.error("Unhandled exception type={} at={}", ex.getClass().getName(),
                trace.length > 0 ? trace[0].toString() : "unknown");
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "error.internal");
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String code, String messageKey) {
        return ResponseEntity.status(status).body(new ApiError(code, messageKey, Instant.now()));
    }
}
