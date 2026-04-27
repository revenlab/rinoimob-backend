package com.rinoimob.exception;

import java.time.Instant;

public record ApiErrorResponse(
        String errorId,
        int status,
        String error,
        String message,
        String path,
        Instant timestamp
) {
}
