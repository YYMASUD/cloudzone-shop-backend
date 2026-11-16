package com.cloudzone.shopbackend.common.error;

import java.time.Instant;

/**
 * Uniform JSON body for every non-2xx response the API emits.
 *
 * <p>{@code code} is the stable, machine-readable value a client switches on
 * (e.g. {@code "email_taken"}); {@code message} is for humans and is free to change.
 * Keeping them separate means a copyedit never breaks client behaviour.
 *
 * @param code      stable client-facing identifier
 * @param message   human-readable detail
 * @param status    HTTP status mirrored in the body for clients that never see headers
 * @param path      request path, useful when logs outlive the response
 * @param timestamp server time of the failure
 */
public record ApiError(
        String code,
        String message,
        int status,
        String path,
        Instant timestamp) {

    public static ApiError of(String code, String message, int status, String path) {
        return new ApiError(code, message, status, path, Instant.now());
    }
}
