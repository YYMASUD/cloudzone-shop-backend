package com.cloudzone.shopbackend.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Revokes the presented refresh token — the credential that would otherwise keep
 * minting access tokens until it expired.
 *
 * @param refreshToken the refresh token to invalidate
 */
public record LogoutRequest(@NotBlank String refreshToken) {
}
