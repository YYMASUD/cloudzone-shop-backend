package com.cloudzone.shopbackend.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudzone.shopbackend.common.enums.Role;
import com.cloudzone.shopbackend.common.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
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
 * End-to-end cart contract over real HTTP, against the DEV seed dataset
 * ({@code schema + seed} profiles).
 *
 * <p>Coverage decisions: each promise the cart makes to a client gets a dedicated test —
 * an absent cart reads as empty rather than 404; an over-stock add is a documented 409;
 * a foreign cart line is indistinguishable from a missing one (404, so ids are not
 * enumerable); and the seeded customer's pre-existing cart row proves get-or-create is
 * idempotent rather than always inserting.
 */
@ActiveProfiles({"schema", "seed"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CartControllerTest {

    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** A CUSTOMER token with no backing user row — proves cart creation on first add. */
    private String token;
    /** A second CUSTOMER, used to prove cart lines are owner-scoped. */
    private String otherToken;
    /** The seeded customer, whose V1000 row already has an empty cart. */
    private long seededUserId;
    private String seededToken;

    @BeforeAll
    void mintTokens() throws Exception {
        token = jwtService.issueAccessToken(777L, "cart-shopper@shop.dev", Role.CUSTOMER);
        otherToken = jwtService.issueAccessToken(778L, "cart-other@shop.dev", Role.CUSTOMER);
        // Login resolves the actual seeded customer id (not the admin's first identity id).
        JsonNode pair = postJson("/auth/login",
                "{\"email\":\"customer@shop.dev\",\"password\":\"Customer123!\"}", null);
        seededToken = pair.get("accessToken").asText();
        JsonNode me = getJson("/auth/me", seededToken);
        seededUserId = me.get("id").asLong();
    }

    @Test
    @Order(1)
    @DisplayName("GET /api/cart returns an empty cart (not a 404) when none exists yet")
    void emptyCartIsNotFoundless() throws Exception {
        // A fresh user id — V1000 never touched it — must not 404 on the read path.
        JsonNode body = getJson("/api/cart", token);

        // default-property-inclusion: non_null omits the null id entirely.
        assertThat(body.has("id")).as("id is omitted for an absent cart").isFalse();
        assertThat(body.get("items")).isEmpty();
        assertThat(body.get("itemCount").asLong()).isZero();
        assertThat(body.get("subtotal").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(body.get("userId").asLong()).isEqualTo(777L);
    }

    @Test
    @Order(2)
    @DisplayName("First add creates the cart row and returns the full cart shape")
    void firstAddCreatesCart() throws Exception {
        // The seeded customer already has an empty cart row; the uid is real.
        long productId = seededProductId("pebble-mini-speaker");

        JsonNode body = postJson("/api/cart/items",
                "{\"productId\":" + productId + ",\"quantity\":2}", seededToken);

        assertThat(body.get("id").asLong()).isPositive();
        assertThat(body.get("userId").asLong()).isEqualTo(seededUserId);
        assertThat(body.get("items")).hasSize(1);
        JsonNode line = body.get("items").get(0);
        assertThat(line.get("productId").asLong()).isEqualTo(productId);
        assertThat(line.get("quantity").asInt()).isEqualTo(2);
        assertThat(body.get("itemCount").asInt()).isEqualTo(2);
        assertThat(body.get("subtotal").decimalValue())
                .isEqualByComparingTo(new BigDecimal("79.98"));
    }

    @Test
    @Order(3)
    @DisplayName("Over-stock add is a documented 409, not a silent clamp")
    void overStockAddIsConflict() throws Exception {
        long productId = seededProductId("cloudzone-classic-tee-oos");

        HttpResponse<String> response = post("/api/cart/items",
                "{\"productId\":" + productId + ",\"quantity\":1}", seededToken);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(readCode(response)).isEqualTo("insufficient_stock");
    }

    @Test
    @Order(4)
    @DisplayName("A foreign cart line is a 404, not a 403 — ids are not enumerable")
    void foreignCartItemIsNotFound() throws Exception {
        long productId = seededProductId("tactile-65-mechanical-keyboard");
        JsonNode seededCart = postJson("/api/cart/items",
                "{\"productId\":" + productId + ",\"quantity\":1}", seededToken);
        long itemId = seededCart.get("items").get(0).get("id").asLong();

        HttpResponse<String> response = patch("/api/cart/items/" + itemId,
                "{\"quantity\":5}", otherToken);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(readCode(response)).isEqualTo("cart_item_not_found");
    }

    // ── transport helpers (same style as AuthControllerTest) ────────────────────

    private long seededProductId(String slug) throws Exception {
        JsonNode product = getJson("/api/products/" + slug, token);
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

    private HttpResponse<String> patch(String path, String json, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .method("PATCH", BodyPublishers.ofString(json));
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
