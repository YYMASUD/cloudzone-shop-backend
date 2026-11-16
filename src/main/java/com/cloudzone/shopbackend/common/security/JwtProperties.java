package com.cloudzone.shopbackend.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT issuance/verification settings, bound from the {@code app.jwt.*} property tree.
 *
 * <p>Key locations default to the classpath pair in {@code src/main/resources/keys}; in
 * production both must be overridden (see DEPLOYMENT.md §5.2) so the private key is
 * mounted as a secret rather than baked into the image.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String privateKeyLocation,
        String publicKeyLocation,
        long accessTokenExpirySeconds,
        long refreshTokenExpirySeconds) {

    public JwtProperties {
        if (privateKeyLocation == null || privateKeyLocation.isBlank()) {
            throw new IllegalStateException("app.jwt.private-key-location must be set");
        }
        if (publicKeyLocation == null || publicKeyLocation.isBlank()) {
            throw new IllegalStateException("app.jwt.public-key-location must be set");
        }
        if (accessTokenExpirySeconds <= 0 || refreshTokenExpirySeconds <= 0) {
            throw new IllegalStateException("JWT expiry values must be positive");
        }
        if (refreshTokenExpirySeconds < accessTokenExpirySeconds) {
            throw new IllegalStateException("Refresh-token expiry must be >= access-token expiry");
        }
    }
}
