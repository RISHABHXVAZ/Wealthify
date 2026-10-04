package com.Wealthify.backend.exception;

import lombok.Getter;

/**
 * Thrown when an action exceeds rate limits or failed-attempt thresholds.
 * Carries the recommended retry-after delay in seconds for the HTTP Retry-After header.
 */
@Getter
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }
}
