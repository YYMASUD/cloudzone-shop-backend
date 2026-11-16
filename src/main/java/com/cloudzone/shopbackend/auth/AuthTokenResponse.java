package com.cloudzone.shopbackend.auth;

/**
 * The token pair a client receives after registering, logging in, or refreshing.
 *
 * <p>{@code tokenType} is always {@code "Bearer"} so a client can paste it straight into
 * an {@code Authorization} header without checking the scheme; {@code expiresIn} is the
 * access token's lifetime in seconds — the refresh token's expiry is intentionally not
 * exposed since the client only ever exchanges it once.
 */
public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn) {
}
