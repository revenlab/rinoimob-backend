package com.rinoimob.exception;

/**
 * Exception for 403 Forbidden responses with a user-facing reason.
 */
public class ForbiddenException extends RuntimeException {
    private final String reason;

    public ForbiddenException(String message, String reason) {
        super(message);
        this.reason = reason;
    }

    public ForbiddenException(String message) {
        this(message, "Access denied");
    }

    public String getReason() {
        return reason;
    }
}
