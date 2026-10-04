package com.Wealthify.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Represents expected, client-facing business logic or domain rule failures.
 * Messages carried by this exception are sanitized and intentional for external presentation.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
    }

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status != null ? status : HttpStatus.BAD_REQUEST;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
