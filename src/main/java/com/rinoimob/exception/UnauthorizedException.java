package com.rinoimob.exception;

/**
 * Exception for 401 Unauthorized responses (invalid/expired token, missing auth)
 */
public class UnauthorizedException extends RuntimeException {
    private final String reason;

    public UnauthorizedException(String message, String reason) {
        super(message);
        this.reason = reason;
    }

    public UnauthorizedException(String message) {
        this(message, "Unauthorized");
    }

    public String getReason() {
        return reason;
    }
}
