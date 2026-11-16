
package com.cloudzone.shopbackend.catalog;

import java.math.BigDecimal;

/**
 * A product as the catalogue list returns it.
 *
 * <p>{@code imageKey} is the object-storage key, not a URL. No storage client or public
 * bucket exists yet, so a URL here would have to be invented; a client builds it once a
 * storage slice owns that concern.
 *
 * <p>{@code inStock} is derived server-side instead of making every client compare
 * {@code stockQuantity} to zero — the definition of "available" belongs in one place.
 *
 * <p>{@code brandSlug}/{@code brandName} are flattened like the category fields. They are
 * omitted when the product is unbranded (the app-wide Jackson {@code non_null} policy), so
 * a client must treat them as optional.
 */
public record ProductResponse(
        Long id,
        String sku,
        String name,
        String slug,
        String description,
        BigDecimal price,
        BigDecimal compareAtPrice,
        String currency,
        int stockQuantity,
        boolean inStock,
        String categorySlug,
        String categoryName,
        String brandSlug,
        String brandName,
        String imageKey) {

    static ProductResponse from(Product product) {
        // category/brand are LAZY; the caller maps inside a transaction.
        Category category = product.getCategory();
        Brand brand = product.getBrand();
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getPrice(),
                product.getCompareAtPrice(),
                product.getCurrency(),
                product.getStockQuantity(),
                product.getStockQuantity() > 0,
                category == null ? null : category.getSlug(),
                category == null ? null : category.getName(),
                brand == null ? null : brand.getSlug(),
                brand == null ? null : brand.getName(),
                product.getImageKey());
    }
}
