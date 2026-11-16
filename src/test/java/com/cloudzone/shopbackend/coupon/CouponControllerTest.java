package com.cloudzone.shopbackend.coupon;

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
 * End-to-end coupon contract over real HTTP, against the DEV seed dataset
 * ({@code schema + seed} profiles) plus coupons the tests create themselves.
 *
 * <p>Coupons have no {@code user_id} FK, so tokens can carry synthetic ids (no user
 * row lookup). Ordering is explicit: the create-path tests run first and populate the
 * admin listing the later tests assert against.
 */
@ActiveProfiles({"schema", "seed"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CouponControllerTest {

    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private String adminToken;
    private String shopperToken;

    @BeforeAll
    void mintTokens() {
        adminToken = jwtService.issueAccessToken(1L, "admin@shop.dev", Role.ADMIN);
        shopperToken = jwtService.issueAccessToken(505L, "coupon-shopper@shop.dev", Role.CUSTOMER);
    }

    @Test
    @Order(1)
    @DisplayName("Admin creates a percentage coupon and the response echoes it")
    void createCouponAsAdmin() throws Exception {
        JsonNode created = postJson("/api/admin/coupons",
                "{\"code\":\"WELCOME10\",\"description\":\"10% off\","
                        + "\"discountType\":\"PERCENT\",\"discountValue\":10}",
                adminToken);
        long id = created.get("id").asLong();

        assertThat(id).isPositive();
        assertThat(created.get("code").asText()).isEqualTo("WELCOME10");
        assertThat(created.get("discountType").asText()).isEqualTo("PERCENT");
        assertThat(created.get("discountValue").decimalValue()).isEqualByComparingTo("10");
        // Operational fields (active/timesRedeemed) are admin-only and absent
        // from the response shape entirely.
        assertThat(created.has("active")).isFalse();
        assertThat(created.has("timesRedeemed")).isFalse();
    }

    @Test
    @Order(2)
    @DisplayName("Creating a duplicate code (case-insensitive) is a 409 coupon_code_taken")
    void duplicateCodeIsConflict() throws Exception {
        HttpResponse<String> response = post("/api/admin/coupons",
                "{\"code\":\"welcome10\",\"discountType\":\"FIXED\",\"discountValue\":5}",
                adminToken);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(readCode(response)).isEqualTo("coupon_code_taken");
    }

    @Test
    @Order(3)
    @DisplayName("A percentage above 100 is a 400 invalid_discount_value")
    void percentOver100IsBadRequest() throws Exception {
        HttpResponse<String> response = post("/api/admin/coupons",
                "{\"code\":\"TOOBIG\",\"discountType\":\"PERCENT\",\"discountValue\":150}",
                adminToken);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(readCode(response)).isEqualTo("invalid_discount_value");
    }

    @Test
    @Order(4)
    @DisplayName("A shopper validates a code against a subtotal and sees only the public shape")
    void validateCouponForShopper() throws Exception {
        JsonNode preview = getJson("/api/coupons/WELCOME10?subtotal=80.00", shopperToken);

        assertThat(preview.get("code").asText()).isEqualTo("WELCOME10");
        assertThat(preview.get("discountValue").decimalValue()).isEqualByComparingTo("10");
        // The public shape carries no operational fields.
        assertThat(preview.has("timesRedeemed")).isFalse();
        assertThat(preview.has("active")).isFalse();
    }

    @Test
    @Order(5)
    @DisplayName("Unknown or unusable codes are all the same 404 coupon_not_found")
    void unknownAndUnusableCodesAreIndistinguishable() throws Exception {
        // Unknown code.
        HttpResponse<String> unknown = send(get("/api/coupons/NOSUCHCODE", shopperToken));
        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(readCode(unknown)).isEqualTo("coupon_not_found");

        // An inactive code must be exactly as invisible as an unknown one.
        JsonNode created = postJson("/api/admin/coupons",
                "{\"code\":\"HIDDEN\",\"discountType\":\"FIXED\",\"discountValue\":5}",
                adminToken);
        patchJson("/api/admin/coupons/" + created.get("id").asLong(),
                "{\"active\":false}", adminToken);
        HttpResponse<String> inactive = send(get("/api/coupons/HIDDEN", shopperToken));
        assertThat(inactive.statusCode()).isEqualTo(404);
        assertThat(readCode(inactive)).isEqualTo("coupon_not_found");
    }

    @Test
    @Order(6)
    @DisplayName("A subtotal below the coupon's minimum is a 400 coupon_min_total")
    void belowMinimumSubtotalIsBadRequest() throws Exception {
        postJson("/api/admin/coupons",
                "{\"code\":\"MIN50\",\"discountType\":\"FIXED\",\"discountValue\":5,"
                        + "\"minOrderTotal\":50}",
                adminToken);

        HttpResponse<String> response = send(get("/api/coupons/MIN50?subtotal=10.00", shopperToken));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(readCode(response)).isEqualTo("coupon_min_total");
    }

    @Test
    @Order(7)
    @DisplayName("The admin surface requires an ADMIN token: CUSTOMER is 403")
    void adminSurfaceIsCustomerForbidden() throws Exception {
        HttpResponse<String> response = send(get("/api/admin/coupons", shopperToken));
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @Order(8)
    @DisplayName("Admin lists coupons newest-first with the stable page envelope")
    void adminListsCouponsPaged() throws Exception {
        JsonNode page = getJson("/api/admin/coupons?page=0&size=100", adminToken);

        assertThat(page.get("content")).isNotEmpty();
        assertThat(page.get("totalElements").asLong()).isGreaterThan(0);
        // Newest first: MIN50 was created last in this class.
        assertThat(page.get("content").get(0).get("code").asText()).isEqualTo("MIN50");
        assertThat(page.has("first")).isTrue();
    }

    @Test
    @Order(9)
    @DisplayName("An admin patch updates only the fields it names")
    void adminPatchUpdatesFields() throws Exception {
        JsonNode list = getJson("/api/admin/coupons?page=0&size=100", adminToken);
        long id = list.get("content").get(0).get("id").asLong();

        JsonNode patched = patchJson("/api/admin/coupons/" + id,
                "{\"description\":\"half price, was 10%\"}", adminToken);

        assertThat(patched.get("description").asText()).isEqualTo("half price, was 10%");
        // Untouched fields survive the patch.
        assertThat(patched.get("code").asText()).isEqualTo("MIN50");
        assertThat(patched.get("discountType").asText()).isEqualTo("FIXED");
    }

    // ── transport helpers (same style as AuthControllerTest) ───────────────────

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

    private JsonNode patchJson(String path, String json, String token) throws Exception {
        HttpResponse<String> response = patch(path, json, token);
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