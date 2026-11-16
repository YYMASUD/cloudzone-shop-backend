package com.cloudzone.shopbackend.wishlist;

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
 * End-to-end wishlist contract over real HTTP, against the DEV seed dataset.
 *
 * <p>Each promise the wishlist makes gets a dedicated test: an absent wishlist reads as
 * empty rather than 404; a duplicate add is a documented 409, not a silent insert; and
 * a foreign item id is indistinguishable from a missing one (404, so ids are not
 * enumerable).
 */
@ActiveProfiles({"schema", "seed"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WishlistControllerTest {

    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private String token;
    private String otherToken;
    private String emptyToken;

    @BeforeAll
    void mintTokens() throws Exception {
        // wishlist_items.user_id is a real FK to users, so the writer needs a uid that
        // exists: register a fresh customer and re-mint with the id the server issued.
        JsonNode pair = postJson("/auth/register",
                "{\"email\":\"wishlist-shopper@shop.dev\",\"password\":\"Secret123!\","
                        + "\"firstName\":\"Willa\",\"lastName\":\"Wishlist\"}", null);
        JsonNode me = getJson("/auth/me", pair.get("accessToken").asText());
        token = jwtService.issueAccessToken(me.get("id").asLong(),
                "wishlist-shopper@shop.dev", Role.CUSTOMER);
        // Independent, never-written user id keeps the empty-list contract isolated.
        emptyToken = jwtService.issueAccessToken(887L, "wishlist-empty@shop.dev", Role.CUSTOMER);
        // Never writes - only proves an id the caller does not own is invisible (404).
        otherToken = jwtService.issueAccessToken(889L, "wishlist-other@shop.dev", Role.CUSTOMER);
    }

    @Test
    @DisplayName("GET /api/wishlist/items returns an empty list (not a 404) when none exist")
    void emptyWishlistIsNotFoundless() throws Exception {
        JsonNode body = getJson("/api/wishlist/items", emptyToken);
        assertThat(body).isEmpty();
    }

    @Test
    @DisplayName("Add-then-list round-trips the product snapshot")
    void addThenList() throws Exception {
        long productId = seededProductId("aurora-wireless-headphones");

        JsonNode created = postJson("/api/wishlist/items",
                "{\"productId\":" + productId + "}", token);
        assertThat(created.get("productId").asLong()).isEqualTo(productId);
        assertThat(created.get("active").asBoolean()).isTrue();

        JsonNode list = getJson("/api/wishlist/items", token);
        assertThat(list).anySatisfy(item -> {
            assertThat(item.get("productId").asLong()).isEqualTo(productId);
            assertThat(item.get("slug").asText()).isEqualTo("aurora-wireless-headphones");
        });
    }

    @Test
    @DisplayName("Adding the same product twice is a documented 409, not a silent duplicate")
    void duplicateAddIsConflict() throws Exception {
        long productId = seededProductId("pebble-mini-speaker");
        String body = "{\"productId\":" + productId + "}";
        postJson("/api/wishlist/items", body, token);        // the first add succeeds

        HttpResponse<String> second = post("/api/wishlist/items", body, token);

        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(readCode(second)).isEqualTo("wishlist_item_exists");
    }

    @Test
    @DisplayName("Removing one's own item drops only that product from the list")
    void ownRemoveDropsTheItem() throws Exception {
        long productId = seededProductId("tactile-65-mechanical-keyboard");
        JsonNode created = postJson("/api/wishlist/items",
                "{\"productId\":" + productId + "}", token);
        long itemId = created.get("id").asLong();

        HttpResponse<String> response = delete("/api/wishlist/items/" + itemId, token);

        assertThat(response.statusCode()).isEqualTo(204);
        JsonNode list = getJson("/api/wishlist/items", token);
        assertThat(list).noneSatisfy(item ->
                assertThat(item.get("productId").asLong()).isEqualTo(productId));
    }

    @Test
    @DisplayName("A foreign wishlist item is a 404, not a 403 - ids are not enumerable")
    void foreignItemIsNotFound() throws Exception {
        long productId = seededProductId("cloudzone-classic-tee");
        JsonNode created = postJson("/api/wishlist/items",
                "{\"productId\":" + productId + "}", token);
        long itemId = created.get("id").asLong();

        HttpResponse<String> response = delete("/api/wishlist/items/" + itemId, otherToken);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(readCode(response)).isEqualTo("wishlist_item_not_found");
    }

    // ── transport helpers (same style as AuthControllerTest) ───────────────────

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

    private HttpResponse<String> delete(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(5))
                .DELETE();
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
