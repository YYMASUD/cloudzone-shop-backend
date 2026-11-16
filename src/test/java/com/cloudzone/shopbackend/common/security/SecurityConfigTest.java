package com.cloudzone.shopbackend.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudzone.shopbackend.common.enums.Role;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * End-to-end check of the filter chain declared in {@link SecurityConfig}, driven over
 * real HTTP against the embedded server rather than through mocked security:
 *
 * <ul>
 *   <li>anonymous calls are rejected with 401 and never reach a handler;
 *   <li>a valid access token is accepted and its {@code roles} claim becomes
 *       {@code ROLE_*} authorities;
 *   <li>expired, tampered, foreign-issuer and <em>refresh</em> tokens are all 401 —
 *       the last one is the {@link AccessTokenValidator} boundary;
 *   <li>health and CORS pre-flight stay public.
 * </ul>
 */
@ActiveProfiles("schema")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityConfigTest {

    private static final String PROTECTED_PATH = "/test/secure/whoami";

    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;
    @Autowired private JwtEncoder jwtEncoder;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    @DisplayName("Anonymous request to a protected path is 401")
    void anonymousIsUnauthorized() throws Exception {
        assertThat(send(get(PROTECTED_PATH, null)).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Valid access token is accepted and its role becomes a ROLE_ authority")
    void validAccessTokenIsAuthorized() throws Exception {
        String token = jwtService.issueAccessToken(5L, "admin@shop.dev", Role.ADMIN);

        HttpResponse<String> response = send(get(PROTECTED_PATH, token));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"subject\":\"admin@shop.dev\"");
        assertThat(response.body()).contains("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Expired access token is 401")
    void expiredTokenIsUnauthorized() throws Exception {
        String expired = encode(JwtService.ISSUER, "access",
                Instant.now().minus(Duration.ofMinutes(5)));

        assertThat(send(get(PROTECTED_PATH, expired)).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Token signed by a foreign issuer is 401")
    void foreignIssuerIsUnauthorized() throws Exception {
        String foreign = encode("https://evil.example.com", "access",
                Instant.now().plus(Duration.ofHours(1)));

        assertThat(send(get(PROTECTED_PATH, foreign)).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Tampered token is 401")
    void tamperedTokenIsUnauthorized() throws Exception {
        String token = jwtService.issueAccessToken(5L, "user@example.com", Role.CUSTOMER);
        char[] chars = token.toCharArray();
        int payloadIndex = token.indexOf('.') + 4;
        chars[payloadIndex] = chars[payloadIndex] == 'A' ? 'B' : 'A';

        assertThat(send(get(PROTECTED_PATH, new String(chars))).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("A refresh token cannot call the API, even though it carries a valid role")
    void refreshTokenIsRejectedOnApiPaths() throws Exception {
        // Both token kinds are signed with the same key and carry the same role, so
        // without AccessTokenValidator a 30-day refresh token would work as an access
        // token for its entire lifetime.
        String refresh = jwtService.issueRefreshToken(5L, "admin@shop.dev", Role.ADMIN);

        assertThat(send(get(PROTECTED_PATH, refresh)).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Health endpoint stays public")
    void healthIsPermitted() throws Exception {
        assertThat(send(get("/actuator/health/liveness", null)).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("CORS pre-flight from an allowed origin is answered without a token")
    void corsPreflightIsPermitted() throws Exception {
        HttpRequest preflight = HttpRequest.newBuilder(uri(PROTECTED_PATH))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")
                .build();

        HttpResponse<String> response = send(preflight);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("http://localhost:3000");
    }

    private HttpRequest get(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(5))
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder.build();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String encode(String issuer, String tokenType, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("user@example.com")
                .issuedAt(Instant.now().minus(Duration.ofHours(2)))
                .expiresAt(expiresAt)
                .claim(AuthPrincipal.CLAIM_USER_ID, 5L)
                .claim(AuthPrincipal.CLAIM_ROLES, List.of(Role.CUSTOMER.name()))
                .claim(AuthPrincipal.CLAIM_TOKEN_TYPE, tokenType)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * A stand-in endpoint so the chain can be exercised before the real controllers
     * exist. It echoes back what Spring Security authenticated, which is exactly what
     * {@link SecurityConfig}'s {@code JwtAuthenticationConverter} is responsible for.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfig {

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {

        @GetMapping(PROTECTED_PATH)
        Map<String, Object> whoami(Authentication authentication) {
            return Map.of(
                    "subject", authentication.getName(),
                    "authorities", authentication.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .toList());
        }
    }
}
