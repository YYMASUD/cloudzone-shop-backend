package com.cloudzone.shopbackend.order;

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
 * End-to-end order contract over real HTTP, against the seed dataset
 * ({@code schema + seed} profiles).
 *
 * <p>Coverage decisions: checkout is proven all the way down — the cart empties and an
 * ORDER_PLACED notification lands. Reads are owner-scoped: a foreign order number is a
 * 404 (not enumerable), and the list is a page envelope, never a bare array.
 */
@ActiveProfiles({"schema", "seed"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderControllerTest {

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

    @BeforeAll
    void setup() throws Exception {
        adminToken = jwtService.issueAccessToken(1L, "admin@shop.dev", Role.ADMIN);
        // Register real shoppers so addresses/carts/orders have an owning user row.
        JsonNode pair = postJson("/auth/register",
                "{\"email\":\"order-shopper@shop.dev\",\"password\":\"Secret123!\","
                        + "\"firstName\":\"Ora\",\"lastName\":\"Shop\"}", null);
        shopperToken = pair.get("accessToken").asText();

        JsonNode other = postJson("/auth/register",
                "{\"email\":\"order-other@shop.dev\",\"password\":\"Secret123!\","
                        + "\"firstName\":\"Oth\",\"lastName\":\"User\"}", null);
        otherToken = other.get("accessToken").asText();

        // An active shipping method the checkout can price against.
        postJson("/api/admin/shipping-methods",
                "{\"code\":\"STD-ORDER\",\"name\":\"Standard Order Post\","
                        + "\"flatRate\":4.99,\"estimatedDays\":5}",
                adminToken);
    }

    @Test
    @Order(1)
    @DisplayName("Checkout on an empty cart is a 400, not a 500")
    void emptyCartCheckout() throws Exception {
        HttpResponse<String> response = post("/api/orders/checkout",
                "{\"shippingAddress\":{\"recipientName\":\"Ora Shop\",\"line1\":\"1 Main St\","
                        + "\"city\":\"Springfield\",\"country\":\"US\"},"
                        + "\"shippingMethodCode\":\"STD-ORDER\",\"paymentMethod\":\"STRIPE\"}",
                shopperToken);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(readCode(response)).isEqualTo("empty_cart");
    }

    @Test
    @Order(2)
    @DisplayName("Checkout turns the cart into an order, empties the cart, and notifies")
    void checkoutCreatesOrder() throws Exception {
        long productId = seededProductId("tactile-65-mechanical-keyboard");
        postJson("/api/cart/items",
                "{\"productId\":" + productId + ",\"quantity\":2}", shopperToken);

        HttpResponse<String> response = post("/api/orders/checkout",
                "{\"shippingAddress\":{\"recipientName\":\"Ora Shop\",\"line1\":\"1 Main St\","
                        + "\"city\":\"Springfield\",\"state\":\"IL\",\"postalCode\":\"62701\","
                        + "\"country\":\"US\"},"
                        + "\"shippingMethodCode\":\"STD-ORDER\",\"paymentMethod\":\"STRIPE\","
                        + "\"notes\":\"leave at door\"}",
                shopperToken);
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(response.body());

        orderNumber = body.get("orderNumber").asText();
        assertThat(orderNumber).matches("ORD-\\d{4}-\\d{6}");
        assertThat(body.get("status").asText()).isEqualTo("PENDING");
        assertThat(body.get("items").size()).isEqualTo(1);
        assertThat(body.get("items").get(0).get("quantity").asInt()).isEqualTo(2);
        assertThat(body.get("shippingCost").decimalValue()).isEqualByComparingTo("4.99");
        assertThat(body.get("total").decimalValue())
                .isEqualByComparingTo(body.get("subtotal").decimalValue()
                        .add(body.get("shippingCost").decimalValue())
                        .subtract(body.get("discountAmount").decimalValue()));

        // The cart was cleared by checkout.
        JsonNode cart = getJson("/api/cart", shopperToken);
        assertThat(cart.get("items").size()).isZero();

        // An ORDER_PLACED notification landed for the shopper.
        JsonNode notifications = getJson("/api/notifications", shopperToken);
        boolean notified = false;
        for (JsonNode n : notifications.get("content")) {
            if ("ORDER_PLACED".equals(n.get("type").asText())
                    && n.get("title").asText().contains(orderNumber)) {
                notified = true;
            }
        }
        assertThat(notified).as("ORDER_PLACED notification for %s", orderNumber).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/orders returns the caller's orders as a page envelope")
    void listIsOwnerScopedPage() throws Exception {
        JsonNode page = getJson("/api/orders", shopperToken);
        assertThat(page.has("content")).isTrue();
        assertThat(page.get("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        boolean found = false;
        for (JsonNode o : page.get("content")) {
            if (orderNumber.equals(o.get("orderNumber").asText())) {
                found = true;
            }
        }
        assertThat(found).isTrue();

        // A different user sees none of the shopper's orders.
        JsonNode otherPage = getJson("/api/orders", otherToken);
        assertThat(otherPage.get("totalElements").asLong()).isZero();
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/orders/{orderNumber} returns the order; a foreign number is 404")
    void getIsOwnerScoped() throws Exception {
        JsonNode order = getJson("/api/orders/" + orderNumber, shopperToken);
        assertThat(order.get("orderNumber").asText()).isEqualTo(orderNumber);

        HttpResponse<String> foreign = send(get("/api/orders/" + orderNumber, otherToken));
        assertThat(foreign.statusCode()).isEqualTo(404);
        assertThat(readCode(foreign)).isEqualTo("order_not_found");
    }

    @Test
    @Order(5)
    @DisplayName("Checkout without a token is a 401; STD-ORDER is retired after the suite")
    void checkoutRequiresAuth() throws Exception {
        HttpResponse<String> response = post("/api/orders/checkout",
                "{\"shippingAddress\":{\"recipientName\":\"X\",\"line1\":\"1 St\","
                        + "\"city\":\"C\",\"country\":\"US\"},"
                        + "\"shippingMethodCode\":\"STD-ORDER\",\"paymentMethod\":\"STRIPE\"}",
                null);
        assertThat(response.statusCode()).isEqualTo(401);

        // Tests share the in-mem database: retire the method we created so other
        // classes' "which methods are active?" assertions stay deterministic.
        JsonNode page = getJson("/api/admin/shipping-methods?page=0&size=100", adminToken);
        for (JsonNode row : page.get("content")) {
            if ("STD-ORDER".equals(row.get("code").asText())) {
                patch("/api/admin/shipping-methods/" + row.get("id").asLong(),
                        "{\"active\":false}", adminToken);
            }
        }
    }

    // ── transport helpers (same style as CartControllerTest) ─────────────────────

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
