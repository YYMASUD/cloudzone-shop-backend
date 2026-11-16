package com.cloudzone.shopbackend.common.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects refresh tokens on the API surface.
 *
 * <p>Access and refresh tokens are signed with the same key, so without this check a
 * 30-day refresh token would be accepted wherever a 1-hour access token is. That would
 * silently defeat the point of short-lived access tokens. Only
 * {@link AuthPrincipal.TokenType#ACCESS} passes here; the refresh endpoint (not part of
 * this filter chain) will verify refresh tokens explicitly.
 */
public class AccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error WRONG_TYPE = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "Token is not an access token",
            null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String type = token.getClaimAsString(AuthPrincipal.CLAIM_TOKEN_TYPE);
        if (AuthPrincipal.TokenType.ACCESS.value().equals(type)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(WRONG_TYPE);
    }
}
