package com.cloudzone.shopbackend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end auth journey over real HTTP, in the same style as {@code SecurityConfigTest}:
 * register produces a token pair, the access token opens {@code GET /auth/me}, the refresh
 * token re-arms the session, and the negative paths all surface the uniform error body.
 *
 * <p>Runs under {@code schema}, so each test registers a unique email rather than relying
 * on seed contents (which deliberately don't exist on this profile).
 */
@ActiveProfiles("schema")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerTest {

    @LocalServerPort private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ── happy path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Register returns a token pair whose access token opens /auth/me")
    void registerThenMeThenRefresh() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";

        // 1. register → token pair
        JsonNode pair = postJson("/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\","
                        + "\"firstName\":\"Ada\",\"lastName\":\"New\"}", null);
        String access = pair.get("accessToken").asText();
        String refresh = pair.get("refreshToken").asText();
        assertThat(access).isNotBlank();
        assertThat(refresh).isNotBlank();
        assertThat(pair.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(pair.get("expiresIn").asLong()).isPositive();

        // 2. access token opens the protected /me and echoes the new account
        JsonNode me = getJson("/auth/me", access);
        assertThat(me.get("email").asText()).isEqualTo(email);
        assertThat(me.get("firstName").asText()).isEqualTo("Ada");
        assertThat(me.get("role").asText()).isEqualTo("CUSTOMER");
        assertThat(me.get("emailVerified").asBoolean()).isFalse();

        // 3. the refresh token (which /me would reject) re-arms the session
        JsonNode renewed = postJson("/auth/refresh",
                "{\"refreshToken\":\"" + refresh + "\"}", null);
        assertThat(renewed.get("accessToken").asText()).isNotBlank();
        assertThat(renewed.get("accessToken").asText()).isNotEqualTo(access);
    }

    // ── negative paths ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Wrong email and wrong password are the same 401 (no account enumeration)")
    void loginFailuresLookIdentical() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        postJson("/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}", null);

        var noSuchUser = post("/auth/login",
                "{\"email\":\"ghost@example.com\",\"password\":\"x\"}", null);
        var wrongPassword = post("/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"Wrong123!\"}", null);

        assertThat(noSuchUser.statusCode()).isEqualTo(401);
        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        // identical code, so clients/attackers can't tell which part was wrong
        assertThat(readCode(noSuchUser)).isEqualTo(readCode(wrongPassword));
    }

    @Test
    @DisplayName("Registering the same email twice is a 409 with the stable email_taken code")
    void duplicateEmailIsConflict() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}";
        postJson("/auth/register", body, null);   // first succeeds

        var second = post("/auth/register", body, null);
        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(readCode(second)).isEqualTo("email_taken");
    }

    @Test
    @DisplayName("An access token cannot be used to refresh the session")
    void accessTokenCannotRefresh() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        JsonNode pair = postJson("/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}", null);

        var response = post("/auth/refresh",
                "{\"refreshToken\":\"" + pair.get("accessToken").asText() + "\"}", null);
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("A forged refresh token is a 401, never a 500")
    void forgedRefreshTokenIsUnauthorized() throws Exception {
        var response = post("/auth/refresh",
                "{\"refreshToken\":\"not.a.real.token\"}", null);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(readCode(response)).isEqualTo("invalid_credentials");
    }

    @Test
    @DisplayName("/auth/me with no token is a 401")
    void meRequiresToken() throws Exception {
        var response = send(get("/auth/me", null));
        assertThat(response.statusCode()).isEqualTo(401);
    }

    // ── transport helpers ───────────────────────────────────────────────────

    private JsonNode postJson(String path, String json, String token) throws Exception {
        HttpResponse<String> response = post(path, json, token);
        assertThat(response.statusCode()).as("status of POST %s", path).isBetween(200, 299);
        return objectMapper.readTree(response.body());
    }

    private JsonNode getJson(String path, String token) throws Exception {
        HttpResponse<String> response = send(get(path, token));
        assertThat(response.statusCode()).as("status of GET %s", path).isBetween(200, 299);
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> post(String path, String json, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(BodyPublishers.ofString(json));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
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

    private String readCode(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body()).get("code").asText();
    }
}
