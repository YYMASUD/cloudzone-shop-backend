package com.cloudzone.shopbackend.review;

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
 * End-to-end review contract over real HTTP, against the DEV seed dataset.
 *
 * <p>{@code reviews.user_id} is a real FK to users, so writers register fresh customers
 * and re-mint tokens with the server-issued uid (the wishlist pattern). Ordering is
 * explicit: the keyboard product accumulates one review that later tests mutate,
 * foreign-id-test against, and finally hand to the admin moderation surface.
 */
@ActiveProfiles({"schema", "seed"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReviewControllerTest {

    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private String reviewerToken;
    private String otherToken;
    private String adminToken;
    private long keyboardId;
    private long pebbleId;
    private long keyboardReviewId;

    @BeforeAll
    void mintTokens() throws Exception {
        reviewerToken = registerAndMint("review-shopper@shop.dev", "Rina", "Reviews");
        otherToken = registerAndMint("second-reviewer@shop.dev", "Rudy", "Review");
        // /api/admin/** is ADMIN-gated by SecurityConfig; the seed admin has uid 1.
        adminToken = jwtService.issueAccessToken(1L, "admin@shop.dev", Role.ADMIN);

        // Reviews FK to products; resolve two active seed products by slug.
        keyboardId = productId("tactile-65-mechanical-keyboard");
        pebbleId = productId("pebble-mini-speaker");
    }

    /** Registers a fresh customer and re-mints a token carrying the server-issued uid. */
    private String registerAndMint(String email, String firstName, String lastName)
            throws Exception {
        JsonNode pair = postJson("/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\","
                        + "\"firstName\":\"" + firstName + "\",\"lastName\":\"" + lastName + "\"}",
                null);
        JsonNode me = getJson("/auth/me", pair.get("accessToken").asText());
        return jwtService.issueAccessToken(
                me.get("id").asLong(), email, Role.CUSTOMER);
    }

    private long productId(String slug) throws Exception {
        return getJson("/api/products/" + slug, reviewerToken).get("id").asLong();
    }

    @Test
    @Order(1)
    @DisplayName("A product's review feed starts paged and empty")
    void listStartsEmpty() throws Exception {
        JsonNode page = getJson("/api/products/" + keyboardId + "/reviews?page=0&size=10",
                reviewerToken);
        assertThat(page.get("totalElements").asLong()).isEqualTo(0);
        assertThat(page.get("content")).isEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("Creating a review echoes the entry with a denormalised author name")
    void createRoundTrips() throws Exception {
        JsonNode created = postJson("/api/products/" + keyboardId + "/reviews",
                "{\"rating\":5,\"title\":\"Superb\","
                        + "\"body\":\"Best keys I have typed on.\"}",
                reviewerToken);

        keyboardReviewId = created.get("id").asLong();
        assertThat(keyboardReviewId).isPositive();
        assertThat(created.get("productId").asLong()).isEqualTo(keyboardId);
        assertThat(created.get("rating").asInt()).isEqualTo(5);
        assertThat(created.get("title").asText()).isEqualTo("Superb");
        assertThat(created.get("body").asText()).isEqualTo("Best keys I have typed on.");
        // Authorship is the display-name snapshot, never the raw account id.
        assertThat(created.get("authorName").asText()).isEqualTo("Rina Reviews");
        assertThat(created.has("userId")).isFalse();
    }

    @Test
    @Order(3)
    @DisplayName("A second review of the same product is a documented 409")
    void secondReviewIs409() throws Exception {
        HttpResponse<String> response = post("/api/products/" + keyboardId + "/reviews",
                "{\"rating\":4,\"title\":\"Again\",\"body\":\"Trying twice.\"}",
                reviewerToken);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(readCode(response)).isEqualTo("review_already_exists");
    }

    @Test
    @Order(4)
    @DisplayName("Ratings outside 1..5 are rejected before the database sees them")
    void ratingBoundsAre400() throws Exception {
        HttpResponse<String> tooHigh = post("/api/products/" + pebbleId + "/reviews",
                "{\"rating\":6,\"title\":\"Nope\",\"body\":\"Above the scale.\"}",
                reviewerToken);
        assertThat(tooHigh.statusCode()).isEqualTo(400);
        assertThat(readCode(tooHigh)).isEqualTo("validation_failed");

        HttpResponse<String> tooLow = post("/api/products/" + pebbleId + "/reviews",
                "{\"rating\":0,\"title\":\"Nope\",\"body\":\"Below the scale.\"}",
                reviewerToken);
        assertThat(tooLow.statusCode()).isEqualTo(400);
        assertThat(readCode(tooLow)).isEqualTo("validation_failed");
    }

    @Test
    @Order(5)
    @DisplayName("The per-product feed shows the review newest-first with no owner id")
    void feedIsPublicAndNewestFirst() throws Exception {
        JsonNode page = getJson("/api/products/" + keyboardId + "/reviews?page=0&size=10",
                otherToken);
        assertThat(page.get("totalElements").asLong()).isEqualTo(1);
        assertThat(page.get("content")).hasSize(1);

        JsonNode row = page.get("content").get(0);
        assertThat(row.get("id").asLong()).isEqualTo(keyboardReviewId);
        assertThat(row.get("rating").asText()).isEqualTo("5");
        assertThat(row.get("authorName").asText()).isEqualTo("Rina Reviews");
        assertThat(row.has("userId")).isFalse();
    }

    @Test
    @Order(6)
    @DisplayName("A foreign review id is a 404, not a 403 - ids are not enumerable")
    void foreignReviewIsNotFound() throws Exception {
        HttpResponse<String> patch = patchJsonBody(
                "/api/products/reviews/" + keyboardReviewId,
                "{\"rating\":1}", otherToken);
        assertThat(patch.statusCode()).isEqualTo(404);
        assertThat(readCode(patch)).isEqualTo("review_not_found");

        HttpResponse<String> delete = delete("/api/products/reviews/" + keyboardReviewId,
                otherToken);
        assertThat(delete.statusCode()).isEqualTo(404);
        assertThat(readCode(delete)).isEqualTo("review_not_found");
        // Still there after the failed foreign delete.
        assertThat(getJson("/api/products/" + keyboardId + "/reviews", reviewerToken)
                .get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    @Order(7)
    @DisplayName("PATCH edits only the provided fields and the feed reflects it")
    void patchEditsPartially() throws Exception {
        HttpResponse<String> response = patchJsonBody(
                "/api/products/reviews/" + keyboardReviewId,
                "{\"rating\":3,\"body\":\"Downgraded after a week.\"}", reviewerToken);
        assertThat(response.statusCode()).isBetween(200, 299);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("rating").asInt()).isEqualTo(3);
        assertThat(body.get("title").asText()).isEqualTo("Superb");      // untouched
        assertThat(body.get("body").asText()).isEqualTo("Downgraded after a week.");

        JsonNode page = getJson("/api/products/" + keyboardId + "/reviews", reviewerToken);
        assertThat(page.get("content").get(0).get("rating").asText()).isEqualTo("3");
    }

    @Test
    @Order(8)
    @DisplayName("PATCH enforces the rating bounds too and leaves the row untouched on 400")
    void patchRatingBoundsAre400() throws Exception {
        HttpResponse<String> response = patchJsonBody(
                "/api/products/reviews/" + keyboardReviewId,
                "{\"rating\":9}", reviewerToken);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(readCode(response)).isEqualTo("validation_failed");

        // The rejected field never landed: the review still carries rating 3.
        JsonNode page = getJson("/api/products/" + keyboardId + "/reviews", reviewerToken);
        assertThat(page.get("content").get(0).get("rating").asText()).isEqualTo("3");
    }

    @Test
    @Order(9)
    @DisplayName("The author deletes an own review; an unknown product is a 404")
    void authorDeletesOwnReview() throws Exception {
        assertThat(delete("/api/products/reviews/" + keyboardReviewId, reviewerToken)
                .statusCode()).isEqualTo(204);
        // Feed is empty again and the id is gone for everyone.
        assertThat(getJson("/api/products/" + keyboardId + "/reviews", reviewerToken)
                .get("totalElements").asLong()).isEqualTo(0);
        HttpResponse<String> again =
                delete("/api/products/reviews/" + keyboardReviewId, reviewerToken);
        assertThat(again.statusCode()).isEqualTo(404);
        assertThat(readCode(again)).isEqualTo("review_not_found");

        // Reviewing an unknown product is a 404 product_not_found.
        HttpResponse<String> ghost = post("/api/products/99999999/reviews",
                "{\"rating\":5,\"title\":\"Ghost\",\"body\":\"No such product.\"}",
                reviewerToken);
        assertThat(ghost.statusCode()).isEqualTo(404);
        assertThat(readCode(ghost)).isEqualTo("product_not_found");
    }

    @Test
    @Order(10)
    @DisplayName("Admin moderates: paged listing across products and a hard delete")
    void adminModerates() throws Exception {
        // Seed one review (second reviewer -> pebble) for the moderation surface.
        JsonNode created = postJson("/api/products/" + pebbleId + "/reviews",
                "{\"rating\":4,\"title\":\"Solid\",\"body\":\"Small but mighty.\"}",
                otherToken);
        long reviewId = created.get("id").asLong();

        // Listing scoped to the product finds it...
        JsonNode scoped = getJson("/api/admin/reviews?productId=" + pebbleId, adminToken);
        assertThat(scoped.get("totalElements").asLong()).isEqualTo(1);
        assertThat(scoped.get("content").get(0).get("id").asLong()).isEqualTo(reviewId);

        // ...and the unscoped listing across all products sees it too.
        JsonNode all = getJson("/api/admin/reviews", adminToken);
        assertThat(all.get("totalElements").asLong()).isGreaterThanOrEqualTo(1);

        // A shopper has no business on the admin surface.
        HttpResponse<String> shopperForbidden =
                delete("/api/admin/reviews/" + reviewId, otherToken);
        assertThat(shopperForbidden.statusCode()).isEqualTo(403);

        HttpResponse<String> removed = delete("/api/admin/reviews/" + reviewId, adminToken);
        assertThat(removed.statusCode()).isEqualTo(204);
        assertThat(getJson("/api/products/" + pebbleId + "/reviews", otherToken)
                .get("totalElements").asLong()).isEqualTo(0);

        HttpResponse<String> onceMore = delete("/api/admin/reviews/" + reviewId, adminToken);
        assertThat(onceMore.statusCode()).isEqualTo(404);
        assertThat(readCode(onceMore)).isEqualTo("review_not_found");
    }

    @Test
    @Order(11)
    @DisplayName("The write surface requires a valid token")
    void writesRequireAuthentication() throws Exception {
        HttpResponse<String> response = post("/api/products/" + pebbleId + "/reviews",
                "{\"rating\":5,\"title\":\"Anon\",\"body\":\"No token.\"}", null);
        assertThat(response.statusCode()).isEqualTo(401);
    }

    // ── transport helpers (same style as WishlistControllerTest) ──────────────

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

    private HttpResponse<String> patchJsonBody(String path, String json, String token)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .method("PATCH", BodyPublishers.ofString(json));
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
