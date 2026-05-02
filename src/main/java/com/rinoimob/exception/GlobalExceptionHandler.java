package com.rinoimob.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        String details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::fieldErrorMessage)
                .collect(Collectors.joining("; "));
        String message = details.isBlank() ? "Validation failed" : details;
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message, request, ex);
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequestExceptions(Exception ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request, ex);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorizedException(
            UnauthorizedException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        response.addHeader("X-Reason", ex.getReason());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, ex);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleForbiddenException(
            ForbiddenException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        response.addHeader("X-Reason", ex.getReason());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request, ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        response.addHeader("X-Reason", "Insufficient permissions");
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Access denied", request, ex);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return buildErrorResponse(status, message, request, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error. Contact support with the provided errorId.",
                request,
                ex
        );
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Exception ex
    ) {
        String errorId = UUID.randomUUID().toString();
        String finalMessage = (message == null || message.isBlank()) ? status.getReasonPhrase() : message;
        ApiErrorResponse response = new ApiErrorResponse(
                errorId,
                status.value(),
                status.getReasonPhrase(),
                finalMessage,
                request.getRequestURI(),
                Instant.now()
        );

        if (status.is5xxServerError()) {
            log.error("errorId={} status={} path={} message={}", errorId, status.value(), request.getRequestURI(), finalMessage, ex);
        } else {
            log.warn("errorId={} status={} path={} message={}", errorId, status.value(), request.getRequestURI(), finalMessage);
        }

        return ResponseEntity.status(status).body(response);
    }

    private String fieldErrorMessage(FieldError error) {
        String defaultMessage = error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage();
        return error.getField() + ": " + defaultMessage;
    }
}
