package com.cloudzone.shopbackend.user;

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
 * End-to-end address-book contract over real HTTP, against the DEV seed dataset.
 *
 * <p>The promises under test: the first saved address becomes the default (a shopper who
 * has addresses always has a default); promoting a second address demotes the first; a
 * foreign id is a 404 not a 403 so ids are not enumerable; and deleting the current
 * default hands the flag to the next address rather than leaving the book default-less.
 */
@ActiveProfiles({"schema", "seed"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AddressControllerTest {

    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private String token;
    private String otherToken;
    private Long firstId;
    private Long secondId;

    @BeforeAll
    void setup() throws Exception {
        // Register a real shopper so addresses.user_id has an owning user row, then re-mint
        // a token carrying that uid — the pattern WishlistControllerTest establishes.
        // The suite shares one H2 store across classes and reruns can collide, so the
        // email carries a random suffix — "email already taken" would break every test.
        String tag = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        JsonNode pair = postJson("/auth/register",
                "{\"email\":\"addr-shopper-" + tag + "@shop.dev\",\"password\":\"Secret123!\","
                        + "\"firstName\":\"Ada\",\"lastName\":\"Dresser\"}", null);
        JsonNode me = getJson("/auth/me", pair.get("accessToken").asText());
        token = jwtService.issueAccessToken(me.get("id").asLong(),
                "addr-shopper-" + tag + "@shop.dev", Role.CUSTOMER);

        // An independent account that never writes — proves foreign ids are invisible.
        JsonNode other = postJson("/auth/register",
                "{\"email\":\"addr-other-" + tag + "@shop.dev\",\"password\":\"Secret123!\","
                        + "\"firstName\":\"Otto\",\"lastName\":\"Other\"}", null);
        otherToken = other.get("accessToken").asText();
    }

    @Test
    @Order(1)
    @DisplayName("A fresh account starts with an empty address book (not a 404)")
    void emptyBookIsList() throws Exception {
        JsonNode body = getJson("/api/addresses", token);
        assertThat(body).isEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("The first saved address becomes the default automatically")
    void firstAddressIsDefault() throws Exception {
        JsonNode created = postJson("/api/addresses",
                "{\"label\":\"HOME\",\"recipientName\":\"Ada Dresser\",\"line1\":\"1 Main St\","
                        + "\"city\":\"Springfield\",\"postalCode\":\"62701\",\"country\":\"us\"}",
                token);
        firstId = created.get("id").asLong();
        assertThat(created.get("isDefault").asBoolean()).isTrue();
        assertThat(created.get("country").asText()).isEqualTo("US"); // normalised
    }

    @Test
    @Order(3)
    @DisplayName("Promoting a second address demotes the first — at most one default")
    void promoteSecondDemotesFirst() throws Exception {
        JsonNode second = postJson("/api/addresses",
                "{\"recipientName\":\"Ada Dresser\",\"line1\":\"9 Oak Ave\",\"city\":\"Chicago\","
                        + "\"postalCode\":\"60601\",\"country\":\"US\",\"isDefault\":true}",
                token);
        secondId = second.get("id").asLong();

        JsonNode list = getJson("/api/addresses", token);
        int defaults = 0;
        for (JsonNode a : list) {
            if (a.get("isDefault").asBoolean()) {
                defaults++;
                assertThat(a.get("id").asLong()).isEqualTo(secondId); // the promoted one
            }
        }
        assertThat(defaults).as("exactly one default").isEqualTo(1);
    }

    @Test
    @Order(4)
    @DisplayName("GET of a foreign address is a 404, not a 403 — ids are not enumerable")
    void foreignAddressIsNotFound() throws Exception {
        HttpResponse<String> response = send(get("/api/addresses/" + firstId, otherToken));
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(readCode(response)).isEqualTo("address_not_found");
    }

    @Test
    @Order(5)
    @DisplayName("A malformed country code is a 400, not a DB truncation")
    void badCountryIs400() throws Exception {
        HttpResponse<String> response = post("/api/addresses",
                "{\"recipientName\":\"X\",\"line1\":\"1 St\",\"city\":\"C\",\"country\":\"USA\"}",
                token);
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    @Order(6)
    @DisplayName("PATCH applies only the supplied fields")
    void patchIsPartial() throws Exception {
        JsonNode patched = patchJson("/api/addresses/" + secondId,
                "{\"label\":\"OFFICE\",\"city\":\"Evanston\"}", token);
        assertThat(patched.get("label").asText()).isEqualTo("OFFICE");
        assertThat(patched.get("city").asText()).isEqualTo("Evanston");
        assertThat(patched.get("line1").asText()).isEqualTo("9 Oak Ave"); // untouched
    }

    @Test
    @Order(7)
    @DisplayName("Deleting the default hands the flag to the remaining address")
    void deleteDefaultPromotesNext() throws Exception {
        HttpResponse<String> deleted = delete("/api/addresses/" + secondId, token);
        assertThat(deleted.statusCode()).isEqualTo(204);

        JsonNode list = getJson("/api/addresses", token);
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("id").asLong()).isEqualTo(firstId);
        assertThat(list.get(0).get("isDefault").asBoolean()).isTrue(); // promoted back
    }

    @Test
    @Order(8)
    @DisplayName("Every route requires a token")
    void authRequired() throws Exception {
        HttpResponse<String> response = send(get("/api/addresses", null));
        assertThat(response.statusCode()).isEqualTo(401);
    }

    // ── transport helpers (same style as WishlistControllerTest) ─────────────────

    private JsonNode postJson(String path, String json, String token) throws Exception {
        HttpResponse<String> response = post(path, json, token);
        assertThat(response.statusCode()).as("status of POST %s", path).isBetween(200, 299);
        return objectMapper.readTree(response.body());
    }

    private JsonNode patchJson(String path, String json, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .method("PATCH", BodyPublishers.ofString(json));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = send(builder.build());
        assertThat(response.statusCode()).as("status of PATCH %s", path).isBetween(200, 299);
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
