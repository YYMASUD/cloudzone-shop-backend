package com.cloudzone.shopbackend.common.security;

import com.cloudzone.shopbackend.common.enums.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues and introspects the application's own RS256 JWTs.
 *
 * <p>The same RSA key pair signs here and verifies in {@code SecurityConfig}, so the
 * resource server needs no network round-trip (no JWKS endpoint) to authenticate a token.
 *
 * <p>Two token kinds share the format and are distinguished by the {@code token_type}
 * claim — see {@link AuthPrincipal.TokenType}. Only access tokens may call the API; the
 * refresh token exists solely to be exchanged at the refresh endpoint, which is why a
 * stolen access token has a one-hour blast radius while a refresh token cannot read data.
 */
@Service
public class JwtService {

    /** Identifies our tokens; validated on the way in so foreign tokens are rejected. */
    public static final String ISSUER = "shop-backend";

    /** Algorithm pinned on both ends — RS256 is what the RSA key pair supports. */
    public static final SignatureAlgorithm SIGNATURE_ALGORITHM = SignatureAlgorithm.RS256;

    private final JwtEncoder encoder;
    private final long accessTokenExpirySeconds;
    private final long refreshTokenExpirySeconds;

    public JwtService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.accessTokenExpirySeconds = properties.accessTokenExpirySeconds();
        this.refreshTokenExpirySeconds = properties.refreshTokenExpirySeconds();
    }

    /** Short-lived token that authorizes API calls. */
    public String issueAccessToken(long userId, String email, Role role) {
        return issue(userId, email, role, AuthPrincipal.TokenType.ACCESS, accessTokenExpirySeconds);
    }

    /** Long-lived token that may only be exchanged for a new access token. */
    public String issueRefreshToken(long userId, String email, Role role) {
        return issue(userId, email, role, AuthPrincipal.TokenType.REFRESH, refreshTokenExpirySeconds);
    }

    private String issue(
            long userId,
            String email,
            Role role,
            AuthPrincipal.TokenType tokenType,
            long ttlSeconds) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(email)
                .id(UUID.randomUUID().toString())       // jti — enables future revocation lists
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofSeconds(ttlSeconds)))
                .claim(AuthPrincipal.CLAIM_USER_ID, userId)
                .claim(AuthPrincipal.CLAIM_ROLES, List.of(role.name()))
                .claim(AuthPrincipal.CLAIM_TOKEN_TYPE, tokenType.value())
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /** Projects verified token claims back into {@link AuthPrincipal}. */
    public AuthPrincipal toPrincipal(Jwt jwt) {
        AuthPrincipal.TokenType type = AuthPrincipal.TokenType.from(jwt.getClaimAsString(AuthPrincipal.CLAIM_TOKEN_TYPE));
        if (type == null) {
            throw new IllegalStateException("Token is missing a recognised " + AuthPrincipal.CLAIM_TOKEN_TYPE + " claim");
        }
        // The converter may hand back an Integer for small values, so go through
        // Number instead of casting straight to Long.
        Number userIdClaim = jwt.getClaim(AuthPrincipal.CLAIM_USER_ID);
        if (userIdClaim == null) {
            throw new IllegalStateException("Token is missing the " + AuthPrincipal.CLAIM_USER_ID + " claim");
        }
        long userId = userIdClaim.longValue();
        List<String> roles = jwt.getClaim(AuthPrincipal.CLAIM_ROLES);
        String roleName = (roles == null || roles.isEmpty()) ? null : roles.get(0);
        if (roleName == null) {
            throw new IllegalStateException("Token is missing the " + AuthPrincipal.CLAIM_ROLES + " claim");
        }
        return new AuthPrincipal(
                userId,
                jwt.getSubject(),
                Role.valueOf(roleName),
                type,
                jwt.getIssuedAt(),
                jwt.getExpiresAt());
    }
}
