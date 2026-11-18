package com.cloudzone.shopbackend.payment;

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
 * End-to-end payment + order-lifecycle contract over real HTTP, against the seed
 * dataset ({@code schema + seed} profiles).
 *
 * <p>Because no live gateway credentials exist in this environment, every capture
 * and refund goes through a provider's offline confirmation path — which exercises
 * the exact same service/transition/notification code a real gateway would. The
 * suite drives one order through PENDING → PAID → REFUNDED and asserts the money
 * bookkeeping (receipt, paidAt, refundAmount) and the order-status moves land.
 */
@ActiveProfiles({"schema", "seed"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentFlowTest {

    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private String adminToken;
    private String shopperToken;
    private String otherToken;
    private String orderNumber;
    private long orderId;

    @BeforeAll
    void setup() throws Exception {
        adminToken = jwtService.issueAccessToken(1L, "admin@shop.dev", Role.ADMIN);
        shopperToken = register("pay-shopper@shop.dev");
        otherToken = register("pay-other@shop.dev");

        // An active shipping method the checkout can price against.
        postJson("/api/admin/shipping-methods",
                "{\"code\":\"STD-PAY\",\"name\":\"Std\",\"flatRate\":5.00,"
                        + "\"currency\":\"USD\",\"active\":true}",
                adminToken);
    }

    @Test
    @Order(1)
    @DisplayName("Checkout creates a PENDING order; paying captures it and marks PAID")
    void payCapturesOrder() throws Exception {
        long productId = seededProductId("tactile-65-mechanical-keyboard");
        postJson("/api/cart/items",
                "{\"productId\":" + productId + ",\"quantity\":1}", shopperToken);
        JsonNode order = postJson("/api/orders/checkout",
                "{\"shippingAddress\":{\"recipientName\":\"Payer\",\"line1\":\"1 St\","
                        + "\"city\":\"C\",\"country\":\"US\"},"
                        + "\"shippingMethodCode\":\"STD-PAY\",\"paymentMethod\":\"STRIPE\"}",
                shopperToken);
        orderNumber = order.get("orderNumber").asText();
        orderId = order.get("id").asLong();
        assertThat(order.get("status").asText()).isEqualTo("PENDING");

        HttpResponse<String> pay = post("/api/orders/" + orderNumber + "/pay",
                null, shopperToken);
        assertThat(pay.statusCode()).isEqualTo(200);
        JsonNode payment = objectMapper.readTree(pay.body());
        assertThat(payment.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(payment.get("receiptNumber").asText()).isNotBlank();
        assertThat(payment.get("paidAt").isNull()).isFalse();

        // The order reflects the capture.
        assertThat(getJson("/api/orders/" + orderNumber, shopperToken)
                .get("status").asText()).isEqualTo("PAID");

        // An ORDER_PAID notification landed alongside the ORDER_PLACED one.
        JsonNode feed = getJson("/api/notifications", shopperToken);
        boolean paid = false;
        for (JsonNode n : feed.get("content")) {
            if ("ORDER_PAID".equals(n.get("type").asText())) {
                paid = true;
            }
        }
        assertThat(paid).as("an ORDER_PAID notification exists").isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("A second capture on an already-PAID order is a 409, not a double charge")
    void doubleCaptureIsConflict() throws Exception {
        HttpResponse<String> retry = post("/api/orders/" + orderNumber + "/pay",
                null, shopperToken);
        assertThat(retry.statusCode()).isEqualTo(409);
        assertThat(readCode(retry)).isEqualTo("order_not_payable");
    }

    @Test
    @Order(3)
    @DisplayName("Paying a foreign order is a 404; the shopper surface is not enumerable")
    void foreignOrderPayIs404() throws Exception {
        HttpResponse<String> foreign = post("/api/orders/" + orderNumber + "/pay",
                null, otherToken);
        assertThat(foreign.statusCode()).isEqualTo(404);
        assertThat(readCode(foreign)).isEqualTo("order_not_found");
    }

    @Test
    @Order(4)
    @DisplayName("Admin refund marks the payment REFUNDED and the order REFUNDED")
    void adminRefundRestocks() throws Exception {
        JsonNode refund = postJson("/api/admin/orders/" + orderId + "/refund",
                "{\"reason\":\"customer changed mind\"}", adminToken);
        assertThat(refund.get("status").asText()).isEqualTo("REFUNDED");
        assertThat(refund.get("refundAmount").decimalValue())
                .isGreaterThan(new java.math.BigDecimal("0"));

        // Admin view shows the order moved to REFUNDED.
        assertThat(getJson("/api/admin/orders/" + orderId, adminToken)
                .get("status").asText()).isEqualTo("REFUNDED");

        // The shopper sees the same terminal state on their own order.
        assertThat(getJson("/api/orders/" + orderNumber, shopperToken)
                .get("status").asText()).isEqualTo("REFUNDED");
    }

    @Test
    @Order(5)
    @DisplayName("Pay/cancel/refund without a token is a 401; STD-PAY retired after the suite")
    void endpointsRequireAuth() throws Exception {
        assertThat(post("/api/orders/" + orderNumber + "/pay", null, null)
                .statusCode()).isEqualTo(401);
        assertThat(post("/api/orders/" + orderNumber + "/cancel", null, null)
                .statusCode()).isEqualTo(401);
        assertThat(post("/api/admin/orders/" + orderId + "/refund",
                "{}", null).statusCode()).isEqualTo(401);

        // Retire the shipping method so other classes' active-method assertions stay stable.
        JsonNode page = getJson("/api/admin/shipping-methods?page=0&size=100", adminToken);
        for (JsonNode row : page.get("content")) {
            if ("STD-PAY".equals(row.get("code").asText())) {
                patch("/api/admin/shipping-methods/" + row.get("id").asLong(),
                        "{\"active\":false}", adminToken);
            }
        }
    }

    // ── transport helpers (same style as OrderControllerTest) ───────────────────

    private String register(String email) throws Exception {
        JsonNode pair = postJson("/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}", null);
        return pair.get("accessToken").asText();
    }

    private long seededProductId(String slug) throws Exception {
        JsonNode product = getJson("/api/products/" + slug, shopperToken);
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
