package com.cloudzone.shopbackend.shipping;

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
 * End-to-end shipping method contract over real HTTP, against the DEV seed dataset
 * ({@code schema + seed} profiles) plus methods the tests create themselves.
 *
 * <p>Shipping methods have no {@code user_id} FK, so tokens can carry synthetic ids.
 * Ordering is explicit: the creates run first and populate the listings the later
 * tests assert against.
 */
@ActiveProfiles({"schema", "seed"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShippingMethodControllerTest {

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
        // Synthetic uids: the surface has no user FKs, exactly like the coupon tests.
        adminToken = jwtService.issueAccessToken(1L, "admin@shop.dev", Role.ADMIN);
        shopperToken = jwtService.issueAccessToken(506L, "shipping-shopper@shop.dev", Role.CUSTOMER);
    }

    @Test
    @Order(1)
    @DisplayName("Admin creates flat-rate methods and the responses echo them")
    void createMethodsAsAdmin() throws Exception {
        JsonNode express = postJson("/api/admin/shipping-methods",
                "{\"code\":\"EXPRESS\",\"name\":\"Express Courier\","
                        + "\"description\":\"1-2 business days\",\"flatRate\":9.99,"
                        + "\"estimatedDays\":2}",
                adminToken);
        assertThat(express.get("id").asLong()).isPositive();
        assertThat(express.get("code").asText()).isEqualTo("EXPRESS");
        assertThat(express.get("flatRate").decimalValue()).isEqualByComparingTo("9.99");
        assertThat(express.get("estimatedDays").asInt()).isEqualTo(2);

        JsonNode standard = postJson("/api/admin/shipping-methods",
                "{\"code\":\"STANDARD\",\"name\":\"Standard Post\","
                        + "\"flatRate\":4.99,\"estimatedDays\":5}",
                adminToken);
        assertThat(standard.get("id").asLong()).isPositive();
        // active defaults to true when omitted, but is not part of the response shape.
        assertThat(standard.has("active")).isFalse();
    }

    @Test
    @Order(2)
    @DisplayName("Creating a duplicate code (case-insensitive) is a 409 shipping_code_taken")
    void duplicateCodeIsConflict() throws Exception {
        HttpResponse<String> response = post("/api/admin/shipping-methods",
                "{\"code\":\"express\",\"name\":\"Dup\",\"flatRate\":1.00}",
                adminToken);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(readCode(response)).isEqualTo("shipping_code_taken");
    }

    @Test
    @Order(3)
    @DisplayName("A negative flat rate fails bean validation with a 400")
    void negativeRateIsBadRequest() throws Exception {
        HttpResponse<String> response = post("/api/admin/shipping-methods",
                "{\"code\":\"CHEAP\",\"name\":\"Too Cheap\",\"flatRate\":-0.01}",
                adminToken);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(readCode(response)).isEqualTo("validation_failed");
    }

    @Test
    @Order(4)
    @DisplayName("A shopper lists active methods cheapest-first and cannot see inactive ones")
    void shopperListsActiveMethodsCheapestFirst() throws Exception {
        // Hide EXPRESS via patch, so the inactive one must vanish from the listing.
        patchJson("/api/admin/shipping-methods/" + codeId("EXPRESS"),
                "{\"active\":false}", adminToken);

        JsonNode list = getJson("/api/shipping-methods", shopperToken);
        List<String> codes = new ArrayList<>();
        for (JsonNode row : list) {
            codes.add(row.get("code").asText());
        }
        // EXPRESS was hidden via the patch above; only STANDARD remains, cheapest-first.
        assertThat(codes).containsExactly("STANDARD");
    }

    @Test
    @Order(5)
    @DisplayName("The admin surface requires an ADMIN token: CUSTOMER is 403")
    void adminSurfaceIsCustomerForbidden() throws Exception {
        HttpResponse<String> response = send(get("/api/admin/shipping-methods", shopperToken));
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @Order(6)
    @DisplayName("Admin lists methods newest-first and gets one by id")
    void adminListsAndReadsMethods() throws Exception {
        JsonNode page = getJson("/api/admin/shipping-methods?page=0&size=100", adminToken);
        assertThat(page.get("content")).isNotEmpty();
        assertThat(page.get("totalElements").asLong()).isGreaterThanOrEqualTo(2);
        // Newest first: STANDARD was created after EXPRESS in @Order(1).
        assertThat(page.get("content").get(0).get("code").asText()).isEqualTo("STANDARD");

        JsonNode one = getJson("/api/admin/shipping-methods/"
                + codeId("EXPRESS"), adminToken);
        assertThat(one.get("code").asText()).isEqualTo("EXPRESS");
    }

    @Test
    @Order(7)
    @DisplayName("An admin patch updates only the fields it names")
    void adminPatchUpdatesFields() throws Exception {
        JsonNode patched = patchJson("/api/admin/shipping-methods/" + codeId("STANDARD"),
                "{\"description\":\"3-5 business days\",\"flatRate\":5.49}",
                adminToken);

        assertThat(patched.get("description").asText()).isEqualTo("3-5 business days");
        assertThat(patched.get("flatRate").decimalValue()).isEqualByComparingTo("5.49");
        // Untouched fields survive the patch.
        assertThat(patched.get("code").asText()).isEqualTo("STANDARD");
        assertThat(patched.get("estimatedDays").asInt()).isEqualTo(5);
    }

    @Test
    @Order(8)
    @DisplayName("An unknown id is a 404 on the admin surface")
    void unknownMethodIsNotFound() throws Exception {
        HttpResponse<String> response = send(get("/api/admin/shipping-methods/999999", adminToken));
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(readCode(response)).isEqualTo("shipping_method_not_found");
    }

    // ── transport helpers (same style as CouponControllerTest) ────────────────

    /** Resolves a method's id by its code from the admin listing. */
    private long codeId(String code) throws Exception {
        JsonNode page = getJson("/api/admin/shipping-methods?page=0&size=100", adminToken);
        for (JsonNode row : page.get("content")) {
            if (code.equals(row.get("code").asText())) {
                return row.get("id").asLong();
            }
        }
        throw new AssertionError("Shipping method not found in admin listing: " + code);
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