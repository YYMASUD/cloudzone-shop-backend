package com.cloudzone.shopbackend.common.error;

import org.springframework.http.HttpStatus;

/**
 * A business-rule failure that maps to a specific HTTP status and a machine-readable
 * {@link ApiError} code.
 *
 * <p>Thrown from the service layer when an invariant the caller can influence is violated
 * (duplicate email, wrong credentials, …). Deliberately distinct from {@link RuntimeException}
 * programming errors, which stay unhandled and surface as a generic 500 rather than leaking
 * internals.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
