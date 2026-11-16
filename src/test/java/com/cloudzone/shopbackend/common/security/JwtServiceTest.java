package com.cloudzone.shopbackend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.cloudzone.shopbackend.common.enums.Role;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Round-trips tokens through the real {@link JwtService} and the real RS256 decoder:
 * sign → verify → claim checks, TTL arithmetic, issuer/type enforcement, and claim
 * projection back into {@link AuthPrincipal}.
 *
 * <p>Uses the hermetic test key pair from {@code src/test/resources/keys} through the
 * {@code schema} profile, so the suite never depends on the git-ignored dev
 * {@code private.pem}.
 */
@ActiveProfiles("schema")
@SpringBootTest
class JwtServiceTest {

    @Autowired private JwtService jwtService;
    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private RsaPemReader pemReader;

    /**
     * Same key, no {@link AccessTokenValidator} — mirrors the refresh endpoint's own
     * trusted verification path. The production API decoder rejects refresh tokens by
     * design, so claim inspection of a refresh token goes through this instead.
     */
    private Jwt decodeRefreshToken(String token) {
        NimbusJwtDecoder raw = NimbusJwtDecoder
                .withPublicKey(pemReader.readPublicKey("classpath:keys/test-public.pem"))
                .build();
        raw.setJwtValidator(JwtValidators.createDefault());
        return raw.decode(token);
    }

    @Test
    @DisplayName("Access token: sign → decode round-trip preserves every claim")
    void accessTokenRoundTrip() {
        String token = jwtService.issueAccessToken(42L, "user@example.com", Role.CUSTOMER);

        Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("user@example.com");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(JwtService.ISSUER);
        assertThat(jwt.getClaimAsString(AuthPrincipal.CLAIM_TOKEN_TYPE)).isEqualTo("access");
        assertThat((Object) jwt.getClaim(AuthPrincipal.CLAIM_USER_ID)).isEqualTo(42L);
        assertThat(jwt.getClaimAsStringList(AuthPrincipal.CLAIM_ROLES)).containsExactly("CUSTOMER");
        assertThat(jwt.getId()).isNotBlank();                       // jti present
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt())
                .isAfter(jwt.getIssuedAt())
                .isCloseTo(jwt.getIssuedAt().plus(Duration.ofHours(1)), within(30, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("A token signed by our key but with a foreign issuer is rejected")
    void foreignIssuerIsRejected() {
        String forged = encodeWithAppKey("https://evil.example.com", "access",
                Instant.now().plus(Duration.ofHours(1)));

        assertThatThrownBy(() -> jwtDecoder.decode(forged))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("iss");
    }

    @Test
    @DisplayName("An already-expired token is rejected")
    void expiredTokenIsRejected() {
        String expired = encodeWithAppKey(JwtService.ISSUER, "access",
                Instant.now().minus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> jwtDecoder.decode(expired))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("A refresh token cannot authenticate an API request")
    void refreshTokenIsNotAnAccessToken() {
        // Both token kinds share one signing key, so without AccessTokenValidator a
        // 30-day refresh token would work as a 1-hour access token.
        String refresh = jwtService.issueRefreshToken(3L, "r@example.com", Role.CUSTOMER);

        assertThatThrownBy(() -> jwtDecoder.decode(refresh))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("A tampered signature is rejected")
    void tamperedTokenIsRejected() {
        String token = jwtService.issueAccessToken(5L, "t@example.com", Role.CUSTOMER);
        char[] chars = token.toCharArray();
        int payloadIndex = token.indexOf('.') + 4;      // inside the payload segment
        chars[payloadIndex] = chars[payloadIndex] == 'A' ? 'B' : 'A';

        assertThatThrownBy(() -> jwtDecoder.decode(new String(chars)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Refresh token: same key, much longer TTL, distinct token_type")
    void refreshTokenHasLongerTtlAndDistinctType() {
        String token = jwtService.issueRefreshToken(7L, "refresh@example.com", Role.ADMIN);

        Jwt jwt = decodeRefreshToken(token);

        assertThat(jwt.getClaimAsString(AuthPrincipal.CLAIM_TOKEN_TYPE)).isEqualTo("refresh");
        assertThat(jwt.getClaimAsStringList(AuthPrincipal.CLAIM_ROLES)).containsExactly("ADMIN");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isGreaterThan(Duration.ofDays(29));
    }

    @Test
    @DisplayName("toPrincipal projects verified claims into AuthPrincipal")
    void toPrincipalProjectsClaims() {
        String token = jwtService.issueAccessToken(9L, "p@example.com", Role.ADMIN);

        AuthPrincipal principal = jwtService.toPrincipal(jwtDecoder.decode(token));

        assertThat(principal.userId()).isEqualTo(9L);
        assertThat(principal.email()).isEqualTo("p@example.com");
        assertThat(principal.role()).isEqualTo(Role.ADMIN);
        assertThat(principal.tokenType()).isEqualTo(AuthPrincipal.TokenType.ACCESS);
        assertThat(principal.authorities()).containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("toPrincipal reports the refresh type when the token is verified on the trusted path")
    void toPrincipalReadsRefreshType() {
        String token = jwtService.issueRefreshToken(11L, "r@example.com", Role.CUSTOMER);

        AuthPrincipal principal = jwtService.toPrincipal(decodeRefreshToken(token));

        assertThat(principal.userId()).isEqualTo(11L);
        assertThat(principal.tokenType()).isEqualTo(AuthPrincipal.TokenType.REFRESH);
    }

    @Test
    @DisplayName("Every token gets a unique jti, so two issues of the same subject differ")
    void tokensAreNeverIdentical() {
        String first = jwtService.issueAccessToken(1L, "same@example.com", Role.CUSTOMER);
        String second = jwtService.issueAccessToken(1L, "same@example.com", Role.CUSTOMER);

        assertThat(first).isNotEqualTo(second);
        assertThat(jwtDecoder.decode(first).getId())
                .isNotEqualTo(jwtDecoder.decode(second).getId());
    }

    /**
     * Signs arbitrary claims with the application's own key so the negative tests
     * exercise the *validator*, not the signature check. {@code issuedAt} sits two
     * hours in the past so a past {@code expiresAt} produces a genuinely expired
     * token instead of one with a nonsensical lifetime.
     */
    private String encodeWithAppKey(String issuer, String tokenType, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("forged@example.com")
                .issuedAt(Instant.now().minus(Duration.ofHours(2)))
                .expiresAt(expiresAt)
                .claim(AuthPrincipal.CLAIM_USER_ID, 1L)
                .claim(AuthPrincipal.CLAIM_ROLES, List.of(Role.CUSTOMER.name()))
                .claim(AuthPrincipal.CLAIM_TOKEN_TYPE, tokenType)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
