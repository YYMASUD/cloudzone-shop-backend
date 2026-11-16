package com.cloudzone.shopbackend.catalog;

import com.cloudzone.shopbackend.common.error.ApiException;
import com.cloudzone.shopbackend.common.web.PageResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side catalogue logic: what is listed, in which order, and what an unknown input
 * means. Controllers stay thin; every decision worth documenting lives here.
 *
 * <p>Two defensive choices:
 * <ul>
 *   <li><strong>Sort keys are allowlisted.</strong> {@code sort} is translated to a
 *       known {@link org.springframework.data.domain.Sort}, never bound straight from
 *       user input — a free-form sort would let a caller order by any property
 *       (including ones that leak information) and could defeat index-backed plans.</li>
 *   <li><strong>Paging is clamped.</strong> A {@code size} far above the cap is coerced,
 *       not rejected, so a misbehaving client cannot make the database materialise an
 *       arbitrarily large page.</li>
 * </ul>
 */
@Service
public class CatalogService {

    /** Hard ceiling on {@code size}; pages larger than this are clamped, not rejected. */
    private static final int MAX_PAGE_SIZE = 100;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final ProductVariantRepository variantRepository;

    public CatalogService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            BrandRepository brandRepository,
            ProductVariantRepository variantRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.variantRepository = variantRepository;
    }

    /** All active top-level categories; children are reachable via {@code parentSlug}. */
    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> categories(Pageable pageable) {
        Page<CategoryResponse> page = categoryRepository
                .findAllByActiveTrueAndParentIsNull(sortByName(pageable))
                .map(CategoryResponse::from);
        return PageResponse.from(page);
    }

    /** All active categories that are direct children of the given slug. */
    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> subcategories(String parentSlug, Pageable pageable) {
        Category parent = categoryRepository.findBySlug(parentSlug)
                .orElseThrow(() -> unknownCategory(parentSlug));
        Page<CategoryResponse> page = categoryRepository
                .findAllByActiveTrueAndParentId(parent.getId(), sortByName(pageable))
                .map(CategoryResponse::from);
        return PageResponse.from(page);
    }

    /**
     * Active products, narrowed by optional category / brand / price-range / free-text
     * filters, with the client-chosen ordering resolved from the allowlist.
     *
     * <p>All filters are AND-combined. Unknown category or brand slugs are loud 404s, not
     * silently empty pages. Price bounds are validated here so a reversed range is a
     * client error (400), not a confusing empty result.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> products(
            String categorySlug,
            String brandSlug,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String q,
            ProductSort sort,
            Pageable pageable) {

        ProductSort ordering = sort == null ? ProductSort.NEWEST : sort;
        if (minPrice != null && maxPrice != null
                && minPrice.compareTo(maxPrice) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_price_range",
                    "minPrice must be less than or equal to maxPrice");
        }

        Long categoryId = categorySlug == null ? null
                : categoryRepository.findBySlug(categorySlug)
                        .orElseThrow(() -> unknownCategory(categorySlug)).getId();
        Long brandId = brandSlug == null ? null
                : brandRepository.findBySlug(brandSlug)
                        .orElseThrow(() -> unknownBrand(brandSlug)).getId();

        Pageable effective = PageRequest.of(
                Math.max(pageable.getPageNumber(), 0),
                clampSize(pageable.getPageSize()),
                ordering.springSort());

        Page<ProductResponse> page = productRepository
                .search(categoryId, brandId, minPrice, maxPrice, blankToNull(q), effective)
                .map(ProductResponse::from);
        return PageResponse.from(page);
    }

    /** One active product by slug; unknown or inactive is the same loud 404. */
    @Transactional(readOnly = true)
    public ProductResponse product(String slug) {
        Product product = productRepository.findBySlug(slug)
                .filter(Product::isActive)
                .orElseThrow(() -> unknownProduct(slug));
        return ProductResponse.from(product);
    }

    /** Active variants of one active product, cheapest first. */
    @Transactional(readOnly = true)
    public List<ProductVariantResponse> variants(String productSlug) {
        Product product = productRepository.findBySlug(productSlug)
                .filter(Product::isActive)
                .orElseThrow(() -> unknownProduct(productSlug));
        return variantRepository
                .findAllByProductIdAndActiveTrueOrderByPriceAscIdAsc(product.getId())
                .stream()
                .map(ProductVariantResponse::from)
                .toList();
    }

    /** All active brands, alphabetical. */
    @Transactional(readOnly = true)
    public PageResponse<BrandResponse> brands(Pageable pageable) {
        Page<BrandResponse> page = brandRepository
                .findAllByActiveTrue(sortByName(pageable))
                .map(BrandResponse::from);
        return PageResponse.from(page);
    }

    /** Categories are navigation: alphabetical order is the only sensible default. */
    private static Pageable sortByName(Pageable pageable) {
        int size = clampSize(pageable.getPageSize());
        return PageRequest.of(Math.max(pageable.getPageNumber(), 0), size,
                Sort.by(Sort.Direction.ASC, "name"));
    }

    private static int clampSize(int requested) {
        return Math.min(Math.max(requested, 1), MAX_PAGE_SIZE);
    }

    private static ApiException unknownCategory(String slug) {
        return new ApiException(HttpStatus.NOT_FOUND, "category_not_found",
                "Unknown category: " + slug);
    }

    private static ApiException unknownBrand(String slug) {
        return new ApiException(HttpStatus.NOT_FOUND, "brand_not_found",
                "Unknown brand: " + slug);
    }

    private static ApiException unknownProduct(String slug) {
        return new ApiException(HttpStatus.NOT_FOUND, "product_not_found",
                "Unknown product: " + slug);
    }

    /** An absent or whitespace-only term is "no filter", not a LIKE against "". */
    private static String blankToNull(String q) {
        return q == null || q.isBlank() ? null : q.trim();
    }
}
