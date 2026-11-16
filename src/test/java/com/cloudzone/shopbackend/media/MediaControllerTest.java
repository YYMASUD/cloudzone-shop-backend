package com.cloudzone.shopbackend.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudzone.shopbackend.common.enums.Role;
import com.cloudzone.shopbackend.common.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end media contract over real HTTP, against the DEV seed dataset, using the
 * filesystem storage backend that the {@code schema} profile selects.
 *
 * <p>Each promise gets a dedicated test: an empty index is a page envelope (not 404);
 * an upload round-trips with a server-generated object key; type and size gates fire
 * before any byte is stored (415/413); and the admin gate is enforced by the security
 * chain itself (403 for shoppers, 401 without a token). Delete is proven all the way
 * down to the stored bytes disappearing.
 */
@ActiveProfiles({"schema", "seed"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MediaControllerTest {

    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final String BOUNDARY = "cloudzone-media-7351";
    private static final byte[] FAKE_PNG = "png-bytes-for-contract-test".getBytes(StandardCharsets.UTF_8);

    private String adminToken;
    private String shopperToken;

    @BeforeAll
    void mintTokens() throws Exception {
        // uid 1 is the seeded admin (media_assets.owner_user_id only needs SOME real
        // user; the admin gate itself reads the token's roles claim, not the DB).
        adminToken = jwtService.issueAccessToken(1L, "admin@shop.dev", Role.ADMIN);
        // A real shopper for the 403 tests: the gate rejects on roles alone, but
        // registering keeps the dataset honest about who these requests come from.
        JsonNode pair = postJson("/auth/register",
                "{\"email\":\"media-shopper@shop.dev\",\"password\":\"Secret123!\","
                        + "\"firstName\":\"Mia\",\"lastName\":\"Media\"}", null);
        JsonNode me = getJson("/auth/me", pair.get("accessToken").asText());
        shopperToken = jwtService.issueAccessToken(me.get("id").asLong(),
                "media-shopper@shop.dev", Role.CUSTOMER);
    }

    @Test
    @Order(1)
    @DisplayName("GET /api/admin/media on an empty index is a page envelope, not a 404")
    void emptyFeedIsPagedEnvelope() throws Exception {
        JsonNode page = getJson("/api/admin/media", adminToken);
        assertThat(page.get("content")).isEmpty();
        assertThat(page.get("totalElements").asLong()).isZero();
        assertThat(page.get("totalPages").asInt()).isZero();
    }

    @Test
    @Order(2)
    @DisplayName("Upload round-trips: asset row + bytes land under a server-generated key")
    void uploadThenGetRoundTrips() throws Exception {
        HttpResponse<String> response = postMultipart("/api/admin/media", adminToken,
                "banner.png", "image/png", FAKE_PNG, "Homepage banner");
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode asset = objectMapper.readTree(response.body());

        long id = asset.get("id").asLong();
        assertThat(asset.get("fileName").asText()).isEqualTo("banner.png");
        assertThat(asset.get("contentType").asText()).isEqualTo("image/png");
        assertThat(asset.get("sizeBytes").asLong()).isEqualTo(FAKE_PNG.length);
        assertThat(asset.get("altText").asText()).isEqualTo("Homepage banner");
        assertThat(asset.get("ownerUserId").asLong()).isEqualTo(1L);
        assertThat(asset.get("objectKey").asText()).startsWith("u1/").endsWith(".png");

        JsonNode fetched = getJson("/api/admin/media/" + id, adminToken);
        assertThat(fetched.get("objectKey").asText()).isEqualTo(asset.get("objectKey").asText());
        assertThat(Files.exists(uploadedFile(asset.get("objectKey").asText())))
                .as("bytes stored under the object key").isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("An upload over 5 MB is a 413 with code media_too_large")
    void oversizedFileIs413() throws Exception {
        byte[] huge = new byte[6 * 1024 * 1024];
        HttpResponse<String> response = postMultipart("/api/admin/media", adminToken,
                "huge.png", "image/png", huge, null);
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(readCode(response)).isEqualTo("media_too_large");
    }

    @Test
    @Order(4)
    @DisplayName("A disallowed content type is a 415 before any byte is stored")
    void disallowedTypeIs415() throws Exception {
        HttpResponse<String> response = postMultipart("/api/admin/media", adminToken,
                "notes.txt", "text/plain", "plain text".getBytes(StandardCharsets.UTF_8), null);
        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(readCode(response)).isEqualTo("unsupported_media_type");
    }

    @Test
    @Order(5)
    @DisplayName("Shoppers get 403 from the /api/admin gate; anonymous callers get 401")
    void adminGateBlocksShoppersAndAnonymous() throws Exception {
        HttpResponse<String> shopperResponse =
                postMultipart("/api/admin/media", shopperToken,
                        "sneaky.png", "image/png", FAKE_PNG, null);
        assertThat(shopperResponse.statusCode()).isEqualTo(403);

        HttpResponse<String> anonymousResponse = postMultipart("/api/admin/media", null,
                "anon.png", "image/png", FAKE_PNG, null);
        assertThat(anonymousResponse.statusCode()).isEqualTo(401);

        HttpResponse<String> shopperList = send(get("/api/admin/media", shopperToken));
        assertThat(shopperList.statusCode()).isEqualTo(403);
    }

    @Test
    @Order(6)
    @DisplayName("The feed lists assets newest first with a clamped page size")
    void feedIsNewestFirstAndClamped() throws Exception {
        HttpResponse<String> second = postMultipart("/api/admin/media", adminToken,
                "promo.pdf", "application/pdf", "%PDF-1.4 tiny".getBytes(StandardCharsets.UTF_8),
                "Promo sheet");
        assertThat(second.statusCode()).isEqualTo(201);

        JsonNode page = getJson("/api/admin/media?size=999", adminToken);
        JsonNode content = page.get("content");
        assertThat(content.size()).isGreaterThanOrEqualTo(2);
        // Newest first: the just-uploaded pdf is the first row.
        assertThat(content.get(0).get("contentType").asText()).isEqualTo("application/pdf");
        assertThat(page.get("size").asInt()).isEqualTo(100);
        assertThat(page.get("totalElements").asLong()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(7)
    @DisplayName("An unknown asset id is a 404 for both GET and DELETE")
    void unknownAssetIs404() throws Exception {
        HttpResponse<String> getResponse = send(get("/api/admin/media/424242", adminToken));
        assertThat(getResponse.statusCode()).isEqualTo(404);
        assertThat(readCode(getResponse)).isEqualTo("media_not_found");

        HttpResponse<String> deleteResponse = delete("/api/admin/media/424242", adminToken);
        assertThat(deleteResponse.statusCode()).isEqualTo(404);
        assertThat(readCode(deleteResponse)).isEqualTo("media_not_found");
    }

    @Test
    @Order(8)
    @DisplayName("Delete removes the row AND the stored bytes")
    void deleteRemovesRowAndBytes() throws Exception {
        HttpResponse<String> created = postMultipart("/api/admin/media", adminToken,
                "doomed.png", "image/png", FAKE_PNG, null);
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode asset = objectMapper.readTree(created.body());
        String objectKey = asset.get("objectKey").asText();
        Path bytes = uploadedFile(objectKey);
        assertThat(Files.exists(bytes)).isTrue();

        HttpResponse<String> response = delete("/api/admin/media/" + asset.get("id").asLong(),
                adminToken);
        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(send(get("/api/admin/media/" + asset.get("id").asLong(), adminToken))
                .statusCode()).isEqualTo(404);
        assertThat(Files.exists(bytes))
                .as("bytes removed from storage with the row").isFalse();
    }

    // ── transport helpers (same style as the review/wishlist suites) ──────────

    private Path uploadedFile(String objectKey) {
        return Path.of("target", "test-uploads", objectKey);
    }

    /** Hand-rolled multipart body: no test dependency beyond the JDK HTTP client. */
    private HttpResponse<String> postMultipart(String path, String token, String fileName,
            String contentType, byte[] bytes, String altText) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(("Content-Disposition: form-data; name=\"file\"; filename=\""
                + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(("Content-Type: " + contentType + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.writeBytes(bytes);
        body.writeBytes(("\r\n--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        if (altText != null) {
            body.writeBytes(("Content-Disposition: form-data; name=\"altText\"\r\n\r\n"
                    + altText + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        body.writeBytes(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(BodyPublishers.ofByteArray(body.toByteArray()));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
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
                .header("Content-Type", "application/json")
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