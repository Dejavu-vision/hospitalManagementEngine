package com.curamatrix.hsm.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handler mapping custom exceptions to proper HTTP status codes.
 * Provides consistent error response format across all API endpoints.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─── Validation errors (400) ─────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
                                                                HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex,
                                                                      HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    // ─── Not Found (404) ─────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    // ─── Conflict / Duplicate (409) ──────────────────────────────

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateResourceException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    // ─── Invalid State Transition (422) ──────────────────────────

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransition(InvalidStateTransitionException ex,
                                                                       HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI());
    }

    // ─── Quota Exceeded (403) ────────────────────────────────────

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<Map<String, Object>> handleQuotaExceeded(QuotaExceededException ex,
                                                                    HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request.getRequestURI());
    }

    // ─── Subscription Expired (403) ──────────────────────────────

    @ExceptionHandler(SubscriptionExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleSubscriptionExpired(SubscriptionExpiredException ex,
                                                                         HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request.getRequestURI());
    }

    // ─── User Deactivated (403) ──────────────────────────────────

    @ExceptionHandler(UserDeactivatedException.class)
    public ResponseEntity<Map<String, Object>> handleUserDeactivated(UserDeactivatedException ex,
                                                                      HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request.getRequestURI());
    }

    // ─── Brute-Force / Rate Limit (429) ──────────────────────────

    @ExceptionHandler(BruteForceProtectionException.class)
    public ResponseEntity<Map<String, Object>> handleBruteForce(BruteForceProtectionException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request.getRequestURI());
    }

    // ─── Access Denied (403) ─────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN,
                "Access denied. You do not have permission to perform this action.",
                request.getRequestURI());
    }

    // ─── Generic RuntimeException (400 / 409) ────────────────────

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex,
                                                             HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String msg = ex.getMessage() == null ? "Unexpected error" : ex.getMessage();
        if (msg.toLowerCase().contains("already exists")) {
            status = HttpStatus.CONFLICT;
        }
        log.warn("RuntimeException on {}: {}", request.getRequestURI(), msg);
        return build(status, msg, request.getRequestURI());
    }

    // ─── Catch-all for unexpected errors (500) ───────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex,
                                                                      HttpServletRequest request) {
        log.error("Unexpected error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI());
    }

    // ─── Response builder ────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message, String path) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", path);
        return ResponseEntity.status(status).body(body);
    }
}
