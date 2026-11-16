package com.cloudzone.shopbackend.catalog;

import com.cloudzone.shopbackend.common.error.ApiException;
import com.cloudzone.shopbackend.common.web.PageResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The read-only catalogue surface.
 *
 * <p>These routes are <strong>not</strong> in {@code SecurityConfig}'s permitAll list, so
 * they inherit {@code anyRequest().authenticated()}: a bearer token is required. That is
 * deliberate for now — the catalogue is reachable, but the security posture is not widened
 * as a side effect of adding a feature. Making browsing public later is an explicit edit
 * to the filter chain (e.g. {@code GET /api/products/**}) plus a matching change to
 * {@code SecurityConfigTest}, which is exactly the review those decisions deserve.
 *
 * <p>Paging parameters are bound as a {@link Pageable} but the service clamps them, so
 * {@code ?size=1000000} cannot exhaust memory.
 */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** Active top-level categories. Children are exposed via {@link #subcategories}. */
    @GetMapping("/categories")
    public PageResponse<CategoryResponse> categories(Pageable pageable) {
        return catalogService.categories(pageable);
    }

    /** Active direct children of {@code slug}; an unknown slug is a 404. */
    @GetMapping("/categories/{slug}/children")
    public PageResponse<CategoryResponse> subcategories(
            @PathVariable String slug, Pageable pageable) {
        return catalogService.subcategories(slug, pageable);
    }

    /**
     * Active products with optional filters, ordered by an allowlisted {@code sort} token.
     *
     * <p>{@code sort} is accepted as a {@link String} and parsed by
     * {@link ProductSort#fromToken} so an unsupported value becomes a documented 400
     * instead of an unhandled conversion error. Filter semantics (AND-combination, loud
     * 404s for unknown slugs, price-range validation) live in {@link CatalogService}.
     *
     * <p>Price bounds are declared as {@link String} and parsed explicitly: a non-numeric
     * {@code minPrice} then yields {@code invalid_parameter} through the shared handler
     * instead of a raw conversion error.
     */
    @GetMapping("/products")
    public PageResponse<ProductResponse> products(
            @RequestParam(name = "category", required = false) String categorySlug,
            @RequestParam(name = "brand", required = false) String brandSlug,
            @RequestParam(name = "minPrice", required = false) String minPrice,
            @RequestParam(name = "maxPrice", required = false) String maxPrice,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "sort", required = false) String sort,
            Pageable pageable) {
        ProductSort ordering;
        try {
            ordering = ProductSort.fromToken(sort);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_sort", e.getMessage());
        }
        return catalogService.products(
                categorySlug, brandSlug, parsePrice("minPrice", minPrice),
                parsePrice("maxPrice", maxPrice), q, ordering, pageable);
    }

    /** One active product by slug; unknown or inactive is a 404. */
    @GetMapping("/products/{slug}")
    public ProductResponse product(@PathVariable String slug) {
        return catalogService.product(slug);
    }

    /** Active variants of one active product, cheapest first; unknown product is a 404. */
    @GetMapping("/products/{slug}/variants")
    public List<ProductVariantResponse> variants(@PathVariable String slug) {
        return catalogService.variants(slug);
    }

    /** All active brands, alphabetical. */
    @GetMapping("/brands")
    public PageResponse<BrandResponse> brands(Pageable pageable) {
        return catalogService.brands(pageable);
    }

    private static BigDecimal parsePrice(String name, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_parameter",
                    name + " must be a decimal number");
        }
    }
}
