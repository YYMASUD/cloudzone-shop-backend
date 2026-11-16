package com.cloudzone.shopbackend.inventory;

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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end inventory contract over real HTTP, against the DEV seed dataset.
 *
 * <p>The two admin surfaces are exercised through the filter chain rather than only at
 * the service layer: a CUSTOMER token must be stopped by {@code SecurityConfig}'s
 * {@code /api/admin/**} matcher (403), while an ADMIN token reaches the handler (200).
 */
@ActiveProfiles({"schema", "seed"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryControllerTest {

    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private String customerToken;
    private String adminToken;

    @BeforeAll
    void mintTokens() throws Exception {
        adminToken = jwtService.issueAccessToken(1L, "admin@shop.dev", Role.ADMIN);
        // stock_reservations.user_id is a real FK to users, so a hold must carry an id
        // that exists: register a fresh customer and re-mint with the id the server issued.
        JsonNode pair = postJson("/auth/register",
                "{\"email\":\"inventory-shopper@shop.dev\",\"password\":\"Secret123!\","
                        + "\"firstName\":\"Ivy\",\"lastName\":\"Stockwell\"}", null);
        JsonNode me = getJson("/auth/me", pair.get("accessToken").asText());
        customerToken = jwtService.issueAccessToken(me.get("id").asLong(),
                "inventory-shopper@shop.dev", Role.CUSTOMER);
    }

    @Test
    @DisplayName("A hold can be placed, listed, released, and its history stays readable")
    void holdReleaseAndHistory() throws Exception {
        long productId = seededProductId("pebble-mini-speaker");

        JsonNode hold = postJson("/api/inventory/reservations",
                "{\"productId\":" + productId + ",\"quantity\":3}", customerToken);
        long holdId = hold.get("id").asLong();
        assertThat(hold.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(hold.get("quantity").asInt()).isEqualTo(3);

        JsonNode history = getJson("/api/inventory/reservations", customerToken);
        assertThat(history).anySatisfy(entry -> {
            assertThat(entry.get("id").asLong()).isEqualTo(holdId);
            assertThat(entry.get("status").asText()).isEqualTo("ACTIVE");
        });

        JsonNode released = postJson("/api/inventory/reservations/" + holdId + "/release",
                null, customerToken);
        assertThat(released.get("status").asText()).isEqualTo("RELEASED");
    }

    @Test
    @DisplayName("Releasing another user's hold is a 404 — reservation ids are not enumerable")
    void foreignReservationIsNotFound() throws Exception {
        long productId = seededProductId("pebble-mini-speaker");
        JsonNode hold = postJson("/api/inventory/reservations",
                "{\"productId\":" + productId + ",\"quantity\":1}", customerToken);
        long holdId = hold.get("id").asLong();

        HttpResponse<String> response = post("/api/inventory/reservations/" + holdId + "/release",
                null, jwtService.issueAccessToken(999L, "other@shop.dev", Role.CUSTOMER));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(readCode(response)).isEqualTo("reservation_not_found");
    }

    @Test
    @DisplayName("Over-holding is a documented 409 insufficient_stock")
    void overHoldIsConflict() throws Exception {
        long productId = seededProductId("cloudzone-classic-tee-oos");

        HttpResponse<String> response = post("/api/inventory/reservations",
                "{\"productId\":" + productId + ",\"quantity\":1}", customerToken);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(readCode(response)).isEqualTo("insufficient_stock");
    }

    @Test
    @DisplayName("The admin surface requires an ADMIN token: CUSTOMER is 403")
    void adminSurfaceIsCustomerForbidden() throws Exception {
        HttpResponse<String> response = send(get(
                "/api/admin/inventory/low-stock-alerts", customerToken));
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("The admin surface opens for an ADMIN token")
    void adminSurfaceIsAdminReachable() throws Exception {
        HttpResponse<String> response = send(get(
                "/api/admin/inventory/low-stock-alerts", adminToken));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    // ── transport helpers ─────────────────────────────────────────────────────

    private long seededProductId(String slug) throws Exception {
        JsonNode product = getJson("/api/products/" + slug, customerToken);
        return product.get("id").asLong();
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
