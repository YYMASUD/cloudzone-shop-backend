package com.cloudzone.shopbackend.catalog;

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
 * End-to-end checks of the read-only catalogue API over real HTTP, against the DEV seed
 * dataset ({@code schema + seed} profiles) whose exact contents
 * {@code SeedDataTest} pins: 3 categories (2 roots + 1 child), 6 products
 * (5 active, 1 inactive, 1 out of stock).
 *
 * <p>Coverage decisions:
 * <ul>
 *   <li>the happy paths assert <em>shape</em> (envelope fields, DTO fields) as well as
 *       values, so a refactor that silently changes the contract fails here;</li>
 *   <li>the defensive rules — inactive rows filtered, unknown category 404, unknown sort
 *       400, oversized page clamped, anonymous caller 401 — each get a dedicated test,
 *       because each is a promise the service makes to its clients.</li>
 * </ul>
 */
@ActiveProfiles({"schema", "seed"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogControllerTest {

    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private String token;

    @BeforeAll
    void mintToken() {
        // No login round trip needed: the catalogue only requires a valid signature.
        // The uid points at no real row on purpose - no catalogue query touches users.
        token = jwtService.issueAccessToken(1L, "catalog-reader@shop.dev", Role.CUSTOMER);
    }

    // ── categories ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/categories lists active roots alphabetically, without children")
    void categoriesListActiveRoots() throws Exception {
        JsonNode body = getJson("/api/categories");

        assertThat(body.get("totalElements").asInt()).isEqualTo(2);
        assertThat(body.get("totalPages").asInt()).isEqualTo(1);
        assertThat(body.get("first").asBoolean()).isTrue();
        assertThat(body.get("last").asBoolean()).isTrue();

        JsonNode content = body.get("content");
        assertThat(content).hasSize(2);
        // name ordering, not insertion order
        assertThat(content.get(0).get("slug").asText()).isEqualTo("apparel");
        assertThat(content.get(1).get("slug").asText()).isEqualTo("electronics");
        // the Audio child is not a root
        assertThat(content.toString()).doesNotContain("\"audio\"");

        JsonNode electronics = content.get(1);
        assertThat(electronics.get("name").asText()).isEqualTo("Electronics");
        assertThat(electronics.get("description").asText()).contains("Gadgets");
        // Null DTO fields are omitted by the app-wide Jackson non_null policy.
        assertThat(electronics.has("parentSlug"))
                .as("roots have no parentSlug").isFalse();
        // the entity's active flag is filtered server-side and never serialised
        assertThat(electronics.has("active")).isFalse();
    }

    @Test
    @DisplayName("GET /api/categories/{slug}/children returns the Audio child")
    void subcategoriesReturnChildren() throws Exception {
        JsonNode body = getJson("/api/categories/electronics/children");

        assertThat(body.get("totalElements").asInt()).isEqualTo(1);
        JsonNode audio = body.get("content").get(0);
        assertThat(audio.get("slug").asText()).isEqualTo("audio");
        assertThat(audio.get("parentSlug").asText()).isEqualTo("electronics");
    }

    @Test
    @DisplayName("Children of an unknown category slug are a loud 404")
    void subcategoriesOfUnknownSlug() throws Exception {
        HttpResponse<String> response = send(get("/api/categories/nope/children", token));
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(readCode(response)).isEqualTo("category_not_found");
    }

    // ── products ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products returns only active products, with the full DTO shape")
    void productsExcludeInactive() throws Exception {
        JsonNode body = getJson("/api/products");

        assertThat(body.get("totalElements").asLong()).isEqualTo(5L);

        JsonNode content = body.get("content");
        assertThat(content.toString()).doesNotContain("archived-gadget");

        JsonNode headphones = matchBySlug(content, "aurora-wireless-headphones");
        assertThat(headphones.get("sku").asText()).isEqualTo("SKU-HEAD-001");
        assertThat(headphones.get("price").decimalValue().toPlainString()).isEqualTo("149.99");
        assertThat(headphones.get("compareAtPrice").decimalValue().toPlainString()).isEqualTo("189.99");
        assertThat(headphones.get("currency").asText()).isEqualTo("USD");
        assertThat(headphones.get("stockQuantity").asInt()).isEqualTo(42);
        assertThat(headphones.get("inStock").asBoolean()).isTrue();
        assertThat(headphones.get("categorySlug").asText()).isEqualTo("audio");
        assertThat(headphones.get("categoryName").asText()).isEqualTo("Audio");
        assertThat(headphones.get("brandSlug").asText()).isEqualTo("aurora");
        assertThat(headphones.get("brandName").asText()).isEqualTo("Aurora Audio");
        assertThat(headphones.get("imageKey").asText())
                .isEqualTo("catalog/aurora-headphones/main.jpg");

        JsonNode keyboard = matchBySlug(content, "tactile-65-mechanical-keyboard");
        assertThat(keyboard.has("brandSlug")).as("brand is optional").isFalse();

        JsonNode outOfStock = matchBySlug(content, "cloudzone-classic-tee-oos");
        assertThat(outOfStock.get("stockQuantity").asInt()).isZero();
        assertThat(outOfStock.get("inStock").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("category=audio narrows the listing to that category's products")
    void productsFilterByCategory() throws Exception {
        JsonNode body = getJson("/api/products?category=audio");

        assertThat(body.get("totalElements").asLong()).isEqualTo(2L);
        JsonNode content = body.get("content");
        assertThat(matchBySlug(content, "aurora-wireless-headphones")).isNotNull();
        assertThat(matchBySlug(content, "pebble-mini-speaker")).isNotNull();
    }

    @Test
    @DisplayName("category=apparel includes the out-of-stock tee but still hides inactive rows")
    void productsFilterKeepsAvailabilityButNotInactive() throws Exception {
        JsonNode body = getJson("/api/products?category=apparel");

        assertThat(body.get("totalElements").asLong()).isEqualTo(2L);
        assertThat(matchBySlug(body.get("content"), "cloudzone-classic-tee")).isNotNull();
        assertThat(matchBySlug(body.get("content"), "cloudzone-classic-tee-oos")).isNotNull();
    }

    @Test
    @DisplayName("sort=price_asc orders by price ascending")
    void productsSortByPriceAscending() throws Exception {
        JsonNode content = getJson("/api/products?sort=price_asc").get("content");

        assertThat(content.get(0).get("slug").asText()).isEqualTo("cloudzone-classic-tee");
        assertThat(content.get(0).get("price").decimalValue())
                .isEqualByComparingTo("24.00");
        // last is the most expensive active product
        assertThat(content.get(content.size() - 1).get("slug").asText())
                .isEqualTo("aurora-wireless-headphones");
    }

    @Test
    @DisplayName("sort=price_desc is the exact reverse of price_asc")
    void productsSortByPriceDescending() throws Exception {
        JsonNode content = getJson("/api/products?sort=price_desc").get("content");

        assertThat(content.get(0).get("slug").asText()).isEqualTo("aurora-wireless-headphones");
        assertThat(content.get(content.size() - 1).get("price").decimalValue())
                .isEqualByComparingTo("24.00");
    }

    @Test
    @DisplayName("An unsupported sort token is a 400, not a 500 and not a silent default")
    void unknownSortIsBadRequest() throws Exception {
        HttpResponse<String> response = send(get("/api/products?sort=cheapest", token));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(readCode(response)).isEqualTo("invalid_sort");
        // the message lists the allowlist so the client can correct itself
        assertThat(response.body()).contains("price_asc");
    }

    @Test
    @DisplayName("A page size above the cap is clamped rather than honoured")
    void oversizedPageIsClamped() throws Exception {
        JsonNode body = getJson("/api/products?size=100000");

        // clamped to 100, which is far more than the 5 seeded rows
        assertThat(body.get("size").asInt()).isEqualTo(100);
        assertThat(body.get("content")).hasSize(5);
        assertThat(body.get("totalElements").asLong()).isEqualTo(5L);
    }

    @Test
    @DisplayName("An unknown category filter is a 404, not an empty 200")
    void unknownCategoryFilterIsNotFound() throws Exception {
        HttpResponse<String> response = send(get("/api/products?category=nope", token));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(readCode(response)).isEqualTo("category_not_found");
    }

    // ── brands, full-text search, detail and variants ─────────────────────────

    @Test
    @DisplayName("GET /api/brands lists only active brands, alphabetically")
    void brandsListActiveAlphabetically() throws Exception {
        JsonNode body = getJson("/api/brands");
        JsonNode content = body.get("content");

        assertThat(body.get("totalElements").asLong()).isEqualTo(3L);
        assertThat(content).hasSize(3);
        assertThat(content.get(0).get("slug").asText()).isEqualTo("aurora");
        assertThat(content.get(0).get("name").asText()).isEqualTo("Aurora Audio");
        assertThat(content.get(1).get("slug").asText()).isEqualTo("cloudzone");
        assertThat(content.get(2).get("slug").asText()).isEqualTo("pebble");
        assertThat(content.toString()).doesNotContain("\"active\"");
    }

    @Test
    @DisplayName("brand, price range and text filters narrow products")
    void productFiltersCombine() throws Exception {
        JsonNode byBrand = getJson("/api/products?brand=aurora");
        assertThat(byBrand.get("totalElements").asLong()).isEqualTo(1L);
        assertThat(byBrand.get("content").get(0).get("slug").asText())
                .isEqualTo("aurora-wireless-headphones");

        JsonNode byPrice = getJson("/api/products?minPrice=30&maxPrice=100&sort=price_asc");
        assertThat(byPrice.get("content")).hasSize(2);
        assertThat(byPrice.get("content").get(0).get("slug").asText())
                .isEqualTo("pebble-mini-speaker");
        assertThat(byPrice.get("content").get(1).get("slug").asText())
                .isEqualTo("tactile-65-mechanical-keyboard");

        JsonNode byText = getJson("/api/products?q=bluetooth");
        assertThat(byText.get("totalElements").asLong()).isEqualTo(1L);
        assertThat(byText.get("content").get(0).get("slug").asText())
                .isEqualTo("aurora-wireless-headphones");

        JsonNode combined = getJson("/api/products?brand=cloudzone&minPrice=20&maxPrice=25&q=tee");
        assertThat(combined.get("totalElements").asLong()).isEqualTo(2L);
        assertThat(combined.get("content").toString()).contains("cloudzone-classic-tee");
    }

    @Test
    @DisplayName("Unknown brand is a 404; malformed and reversed price ranges are 400")
    void productFilterErrorsAreClientErrors() throws Exception {
        HttpResponse<String> unknownBrand = send(get("/api/products?brand=missing", token));
        assertThat(unknownBrand.statusCode()).isEqualTo(404);
        assertThat(readCode(unknownBrand)).isEqualTo("brand_not_found");

        HttpResponse<String> malformedPrice = send(get("/api/products?minPrice=cheap", token));
        assertThat(malformedPrice.statusCode()).isEqualTo(400);
        assertThat(readCode(malformedPrice)).isEqualTo("invalid_parameter");

        HttpResponse<String> reversedRange = send(get("/api/products?minPrice=50&maxPrice=20", token));
        assertThat(reversedRange.statusCode()).isEqualTo(400);
        assertThat(readCode(reversedRange)).isEqualTo("invalid_price_range");
    }

    @Test
    @DisplayName("GET /api/products/{slug} returns one product, with flattened brand data")
    void productDetailIsReturnedBySlug() throws Exception {
        JsonNode detail = getJson("/api/products/aurora-wireless-headphones");

        assertThat(detail.get("slug").asText()).isEqualTo("aurora-wireless-headphones");
        assertThat(detail.get("brandSlug").asText()).isEqualTo("aurora");
        assertThat(detail.get("categorySlug").asText()).isEqualTo("audio");
    }

    @Test
    @DisplayName("GET /api/products/{slug}/variants returns active variants and effective prices")
    void productVariantsAreActiveAndHaveEffectivePrice() throws Exception {
        JsonNode variants = getJson("/api/products/aurora-wireless-headphones/variants");

        assertThat(variants).hasSize(2);
        assertThat(variants.toString()).doesNotContain("SKU-HW-NVY");
        assertThat(variants.get(0).get("sku").asText()).isEqualTo("SKU-HW-BLK");
        assertThat(variants.get(0).get("effectivePrice").decimalValue())
                .isEqualByComparingTo("149.99");
        assertThat(variants.get(0).get("stockQuantity").asInt()).isEqualTo(42);
        assertThat(variants.get(0).get("inStock").asBoolean()).isTrue();

        JsonNode keyboard = getJson("/api/products/tactile-65-mechanical-keyboard/variants");
        assertThat(keyboard).hasSize(2);
        assertThat(keyboard.get(0).get("effectivePrice").decimalValue())
                .isEqualByComparingTo("89.50");
        assertThat(keyboard.get(1).get("effectivePrice").decimalValue())
                .isEqualByComparingTo("94.50");
    }

    @Test
    @DisplayName("A missing product detail or variants resource is a product 404")
    void unknownProductDetailAndVariantsAreNotFound() throws Exception {
        HttpResponse<String> detail = send(get("/api/products/missing-product", token));
        assertThat(detail.statusCode()).isEqualTo(404);
        assertThat(readCode(detail)).isEqualTo("product_not_found");

        HttpResponse<String> variants = send(get("/api/products/missing-product/variants", token));
        assertThat(variants.statusCode()).isEqualTo(404);
        assertThat(readCode(variants)).isEqualTo("product_not_found");
    }

    // ── security ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("The catalogue requires a token: anonymous access is 401")
    void catalogueRequiresAuthentication() throws Exception {
        assertThat(send(get("/api/products", null)).statusCode()).isEqualTo(401);
        assertThat(send(get("/api/categories", null)).statusCode()).isEqualTo(401);
    }
    // ── error-surface guards (the handler that turns protocol errors into ApiError) ──

    @Test
    @DisplayName("An unknown path under /api is a 404 ApiError, not a 500")
    void unknownPathIsNotFound() throws Exception {
        HttpResponse<String> response = send(get("/api/not-a-route", token));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(readCode(response)).isEqualTo("not_found");
    }

    @Test
    @DisplayName("A method the route does not support is a 405, not a 500")
    void wrongMethodIsMethodNotAllowed() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/products"))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token)
                .DELETE()
                .build();
        HttpResponse<String> response = send(request);

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(readCode(response)).isEqualTo("method_not_allowed");
    }
    // ── transport helpers (mirror AuthControllerTest) ─────────────────────────

    /** GETs {@code path} with the shared catalog-reader token and returns the JSON body. */
    private JsonNode getJson(String path) throws Exception {
        return getJson(path, token);
    }

    private JsonNode getJson(String path, String token) throws Exception {
        HttpResponse<String> response = send(get(path, token));
        assertThat(response.statusCode()).as("status of GET %s", path).isBetween(200, 299);
        return objectMapper.readTree(response.body());
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

    /** Pulls the stable machine-readable {@code code} out of an error body. */
    private String readCode(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body()).get("code").asText();
    }

    /** Finds a product/category entry by slug, or returns null when absent. */
    private JsonNode matchBySlug(JsonNode content, String slug) {
        for (JsonNode item : content) {
            if (slug.equals(item.get("slug").asText())) {
                return item;
            }
        }
        return null;
    }
}

