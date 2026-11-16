package com.cloudzone.shopbackend.auth;

import jakarta.validation.constraints.NotBlank;

/** Holds a previously-issued refresh token, exchanged for a new token pair. */
public record RefreshRequest(

        @NotBlank(message = "refreshToken must not be blank")
        String refreshToken) {
}
