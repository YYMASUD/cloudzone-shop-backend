package com.cloudzone.shopbackend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudzone.shopbackend.common.enums.Role;
import com.cloudzone.shopbackend.common.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * The admin dashboard endpoint: admin-only, and it always reports every
 * {@code OrderStatus} key so a client can render zeros without null checks.
 */
@ActiveProfiles({"schema", "seed"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminDashboardTest {

    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    @DisplayName("An admin gets a full stats snapshot with every status key present")
    void adminSeesDashboard() throws Exception {
        String admin = jwtService.issueAccessToken(1L, "admin@shop.dev", Role.ADMIN);

        HttpResponse<String> response = get("/api/admin/dashboard", admin);
        assertThat(response.statusCode()).isEqualTo(200);

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("users").get("total").asLong()).isNotNegative();
        assertThat(body.get("users").get("admins").asLong()).isPositive();
        assertThat(body.get("orders").has("PENDING")).isTrue();
        assertThat(body.get("orders").has("DELIVERED")).isTrue();
        assertThat(body.get("revenue").isNumber()).isTrue();
        assertThat(body.get("revenueCurrency").asText()).isEqualTo("USD");
    }

    @Test
    @DisplayName("A customer is forbidden from the dashboard")
    void customerIsForbidden() throws Exception {
        String customer = jwtService.issueAccessToken(2L, "customer@shop.dev", Role.CUSTOMER);

        HttpResponse<String> response = get("/api/admin/dashboard", customer);
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("No token is a 401")
    void anonymousIsUnauthorized() throws Exception {
        assertThat(get("/api/admin/dashboard", null).statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
