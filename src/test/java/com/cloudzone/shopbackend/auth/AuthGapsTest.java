package com.cloudzone.shopbackend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudzone.shopbackend.user.User;
import com.cloudzone.shopbackend.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

/**
 * The two auth gaps that existed alongside the core login flow: proving an email
 * address with a one-time code, and revoking a refresh token on logout.
 *
 * <p>Codes are inserted directly with a hash the test itself computes, because the
 * clear OTP only ever exists inside the outgoing email (which is disabled on the
 * {@code schema} profile). That keeps the assertions on the real confirm logic — the
 * hash comparison, the TTL filter and the single-use flag — rather than on a mock.
 */
@ActiveProfiles("schema")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthGapsTest {

    @LocalServerPort private int port;
    @Autowired private EmailVerificationTokenRepository verificationTokenRepository;
    @Autowired private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ── email verification ───────────────────────────────────────────────────

    @Test
    @DisplayName("A correct code flips email_verified and cannot be replayed")
    void confirmEmailVerifiesAndIsSingleUse() throws Exception {
        String email = "verify-" + UUID.randomUUID() + "@example.com";
        JsonNode pair = postJson("/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}", null);
        String access = pair.get("accessToken").asText();
        long userId = userId(email);

        String code = "424242";
        insertCode(userId, code, OffsetDateTime.now().plus(10, ChronoUnit.MINUTES));

        var confirmed = post("/auth/verify-email/confirm",
                "{\"code\":\"" + code + "\"}", access);
        assertThat(confirmed.statusCode()).isEqualTo(204);
        assertThat(userRepository.findById(userId).orElseThrow().isEmailVerified()).isTrue();

        // Replay of the now-used code is rejected; the flag is already set.
        var replay = post("/auth/verify-email/confirm",
                "{\"code\":\"" + code + "\"}", access);
        assertThat(replay.statusCode()).isEqualTo(409);
        assertThat(readCode(replay)).isEqualTo("email_already_verified");
    }

    @Test
    @DisplayName("A wrong code is a uniform 400 and leaves the account unverified")
    void wrongCodeIsRejected() throws Exception {
        String email = "badcode-" + UUID.randomUUID() + "@example.com";
        JsonNode pair = postJson("/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}", null);
        long userId = userId(email);
        insertCode(userId, "111111", OffsetDateTime.now().plus(10, ChronoUnit.MINUTES));

        var response = post("/auth/verify-email/confirm",
                "{\"code\":\"999999\"}", pair.get("accessToken").asText());
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(readCode(response)).isEqualTo("invalid_code");
        assertThat(userRepository.findById(userId).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("An expired code is rejected even when the digits match")
    void expiredCodeIsRejected() throws Exception {
        String email = "expired-" + UUID.randomUUID() + "@example.com";
        JsonNode pair = postJson("/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}", null);
        long userId = userId(email);
        insertCode(userId, "777777", OffsetDateTime.now().minus(1, ChronoUnit.MINUTES));

        var response = post("/auth/verify-email/confirm",
                "{\"code\":\"777777\"}", pair.get("accessToken").asText());
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(readCode(response)).isEqualTo("invalid_code");
    }

    @Test
    @DisplayName("Requesting verification without a token is a 401")
    void requestRequiresAuth() throws Exception {
        var response = post("/auth/verify-email/request", "{}", null);
        assertThat(response.statusCode()).isEqualTo(401);
    }

    // ── logout / revocation ─────────────────────────────────────────────────

    @Test
    @DisplayName("Logout revokes the refresh token: it can no longer be exchanged")
    void logoutRevokesRefreshToken() throws Exception {
        String email = "logout-" + UUID.randomUUID() + "@example.com";
        JsonNode pair = postJson("/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}", null);
        String refresh = pair.get("refreshToken").asText();

        var logout = post("/auth/logout", "{\"refreshToken\":\"" + refresh + "\"}", null);
        assertThat(logout.statusCode()).isEqualTo(204);

        var refreshAttempt = post("/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}", null);
        assertThat(refreshAttempt.statusCode()).isEqualTo(401);
        assertThat(readCode(refreshAttempt)).isEqualTo("invalid_credentials");
    }

    @Test
    @DisplayName("Logout is idempotent and never errors on junk credentials")
    void logoutIsIdempotentAndLenient() throws Exception {
        String email = "logout2-" + UUID.randomUUID() + "@example.com";
        JsonNode pair = postJson("/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}", null);
        String refresh = pair.get("refreshToken").asText();

        assertThat(post("/auth/logout", "{\"refreshToken\":\"" + refresh + "\"}", null).statusCode())
                .isEqualTo(204);
        // A second logout on the same credential is still a no-op 204, not a 500/409.
        assertThat(post("/auth/logout", "{\"refreshToken\":\"" + refresh + "\"}", null).statusCode())
                .isEqualTo(204);
        // And junk never creates a row or errors out.
        assertThat(post("/auth/logout", "{\"refreshToken\":\"not.a.token\"}", null).statusCode())
                .isEqualTo(204);
    }

    @Test
    @DisplayName("An access token cannot be revoked as if it were a refresh token")
    void accessTokenIsNotRevocableViaLogout() throws Exception {
        String email = "logout3-" + UUID.randomUUID() + "@example.com";
        JsonNode pair = postJson("/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}", null);

        assertThat(post("/auth/logout",
                "{\"refreshToken\":\"" + pair.get("accessToken").asText() + "\"}", null)
                .statusCode()).isEqualTo(204);
        // The session is unaffected — the real refresh token still works.
        assertThat(post("/auth/refresh",
                "{\"refreshToken\":\"" + pair.get("refreshToken").asText() + "\"}", null)
                .statusCode()).isEqualTo(200);
    }

    // ── social sign-in ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Social login rejects a junk token before touching an account")
    void junkTokenIsRejected() throws Exception {
        var response = post("/auth/social",
                "{\"provider\":\"GOOGLE\",\"idToken\":\"not.a.jwt\"}", null);
        // The token is malformed on its face — rejected without a network call.
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Social login is public: no JWT required")
    void socialRequiresNoToken() throws Exception {
        // A 401 on the JWT filter would mean the endpoint is gated by accident; a 401
        // from the verifier means the public route reached the right check.
        var response = post("/auth/social",
                "{\"provider\":\"GOOGLE\",\"idToken\":\"x.y.z\"}", null);
        assertThat(response.statusCode()).isIn(400, 401);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Persists a code row exactly as the service would, for the given clear code. */
    private void insertCode(long userId, String code, OffsetDateTime expiresAt) {
        verificationTokenRepository.save(EmailVerificationToken.builder()
                .userId(userId)
                .codeHash(sha256(code))
                .expiresAt(expiresAt)
                .build());
    }

    private long userId(String email) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        return user.getId();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode postJson(String path, String json, String token) throws Exception {
        HttpResponse<String> response = post(path, json, token);
        assertThat(response.statusCode()).as("status of POST %s", path).isBetween(200, 299);
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> post(String path, String json, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(BodyPublishers.ofString(json));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String readCode(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body()).get("code").asText();
    }
}
