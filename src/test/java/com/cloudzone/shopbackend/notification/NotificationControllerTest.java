package com.cloudzone.shopbackend.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudzone.shopbackend.common.enums.Role;
import com.cloudzone.shopbackend.common.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end notification contract over real HTTP, against the DEV seed dataset.
 *
 * <p>{@code notifications.user_id} is a real FK to users, so the writer registers a
 * fresh customer and re-mints a token with the server-issued uid (the wishlist pattern).
 * Ordering is explicit: admin sends run first and populate the feed the later tests
 * assert against.
 */
@ActiveProfiles({"schema", "seed"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationControllerTest {

    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private String adminToken;
    private String shopperToken;
    private long shopperUserId;

    @BeforeAll
    void mintTokens() throws Exception {
        // /api/admin/** is ADMIN-gated by SecurityConfig, so the admin token carries a
        // seed admin's uid — sending only writes to the target user, never the sender.
        adminToken = jwtService.issueAccessToken(1L, "admin@shop.dev", Role.ADMIN);

        // The feed writer needs a uid that exists: register, then re-mint with the id
        // the server issued (synthetic ids would violate notifications_user_id_fkey).
        JsonNode pair = postJson("/auth/register",
                "{\"email\":\"notify-shopper@shop.dev\",\"password\":\"Secret123!\","
                        + "\"firstName\":\"Nora\",\"lastName\":\"Notify\"}", null);
        JsonNode me = getJson("/auth/me", pair.get("accessToken").asText());
        shopperUserId = me.get("id").asLong();
        shopperToken = jwtService.issueAccessToken(
                shopperUserId, "notify-shopper@shop.dev", Role.CUSTOMER);
    }

    @Test
    @Order(1)
    @DisplayName("Admin sends notifications; the response echoes the delivered entry")
    void adminSendsNotifications() throws Exception {
        JsonNode first = postJson("/api/admin/notifications/send",
                "{\"userId\":" + shopperUserId + ",\"type\":\"PROMOTION\","
                        + "\"title\":\"Spring sale\",\"body\":\"20% off everything\","
                        + "\"link\":\"/promo/spring\"}",
                adminToken);
        assertThat(first.get("id").asLong()).isPositive();
        assertThat(first.get("type").asText()).isEqualTo("PROMOTION");
        assertThat(first.get("title").asText()).isEqualTo("Spring sale");
        assertThat(first.get("readAt")).isNull();

        postJson("/api/admin/notifications/send",
                "{\"userId\":" + shopperUserId + ",\"type\":\"SYSTEM\","
                        + "\"title\":\"Welcome\"}",
                adminToken);
    }

    @Test
    @Order(2)
    @DisplayName("Sending to an unknown user is a 404 user_not_found")
    void sendToUnknownUserIsNotFound() throws Exception {
        HttpResponse<String> response = post("/api/admin/notifications/send",
                "{\"userId\":99999999,\"type\":\"SYSTEM\",\"title\":\"Ghost\"}",
                adminToken);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(readCode(response)).isEqualTo("user_not_found");
    }

    @Test
    @Order(3)
    @DisplayName("The feed lists the caller's entries newest-first, paged")
    void feedIsOwnerScopedAndNewestFirst() throws Exception {
        JsonNode page = getJson("/api/notifications?page=0&size=10", shopperToken);
        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        assertThat(page.get("content")).hasSize(2);

        List<String> types = new ArrayList<>();
        for (JsonNode row : page.get("content")) {
            types.add(row.get("type").asText());
        }
        // Newest first: SYSTEM was sent after PROMOTION in @Order(1).
        assertThat(types).containsExactly("SYSTEM", "PROMOTION");
        // Every entry is unread and carries no owner id.
        for (JsonNode row : page.get("content")) {
            assertThat(row.get("readAt")).isNull();
            assertThat(row.has("userId")).isFalse();
        }
    }

    @Test
    @Order(4)
    @DisplayName("The unread badge counts only entries without read_at")
    void unreadCountMatchesFeed() throws Exception {
        JsonNode badge = getJson("/api/notifications/unread-count", shopperToken);
        assertThat(badge.get("unread").asLong()).isEqualTo(2);
    }

    @Test
    @Order(5)
    @DisplayName("Marking one own notification read stamps read_at exactly once")
    void markReadStampsOnce() throws Exception {
        long id = firstIdOfType("PROMOTION");
        JsonNode patched = patchJson("/api/notifications/" + id + "/read", shopperToken);

        assertThat(patched.get("readAt")).isNotNull();
        // Idempotent: re-marking a read entry does not error or change anything.
        JsonNode again = patchJson("/api/notifications/" + id + "/read", shopperToken);
        assertThat(again.get("readAt").asText()).isEqualTo(patched.get("readAt").asText());

        JsonNode badge = getJson("/api/notifications/unread-count", shopperToken);
        assertThat(badge.get("unread").asLong()).isEqualTo(1);
    }

    @Test
    @Order(6)
    @DisplayName("A foreign notification id is a 404, not a 403 - ids are not enumerable")
    void foreignNotificationIsNotFound() throws Exception {
        // An id the shopper does not own: the admin send targeted shopperUserId only.
        HttpResponse<String> response = patch("/api/notifications/99999999/read", shopperToken);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(readCode(response)).isEqualTo("notification_not_found");
    }

    @Test
    @Order(7)
    @DisplayName("Read-all transitions every remaining unread row in one call")
    void markAllReadClearsTheBadge() throws Exception {
        JsonNode response = patchJson("/api/notifications/read-all", shopperToken);
        assertThat(response.get("updated").asLong()).isEqualTo(1);

        JsonNode badge = getJson("/api/notifications/unread-count", shopperToken);
        assertThat(badge.get("unread").asLong()).isEqualTo(0);

        // A second run is a valid no-op.
        JsonNode again = patchJson("/api/notifications/read-all", shopperToken);
        assertThat(again.get("updated").asLong()).isEqualTo(0);
    }

    @Test
    @Order(8)
    @DisplayName("The feed surface requires a valid token")
    void feedRequiresAuthentication() throws Exception {
        HttpResponse<String> response = send(get("/api/notifications", null));
        assertThat(response.statusCode()).isEqualTo(401);
    }

    // ── transport helpers (same style as CouponControllerTest) ────────────────

    /** Resolves the newest feed entry of the given type from the paged feed. */
    private long firstIdOfType(String type) throws Exception {
        JsonNode page = getJson("/api/notifications?page=0&size=10", shopperToken);
        for (JsonNode row : page.get("content")) {
            if (type.equals(row.get("type").asText())) {
                return row.get("id").asLong();
            }
        }
        throw new AssertionError("No " + type + " notification in the feed");
    }

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

    private JsonNode patchJson(String path, String token) throws Exception {
        HttpResponse<String> response = patch(path, token);
        assertThat(response.statusCode()).as("status of PATCH %s", path).isBetween(200, 299);
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> post(String path, String json, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(json == null ? BodyPublishers.noBody() : BodyPublishers.ofString(json));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    private HttpResponse<String> patch(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .method("PATCH", BodyPublishers.noBody());
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