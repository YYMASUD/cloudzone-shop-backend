package com.cloudzone.shopbackend.wishlist;

import com.cloudzone.shopbackend.catalog.Product;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One row of the caller's wishlist. Catalogue data (name/slug/price) is resolved from
 * the product table at read time; {@code active} is echoed so a client can grey out an
 * entry whose product has been delisted instead of silently hiding it.
 */
public record WishlistItemResponse(
        Long id,
        Long productId,
        String name,
        String slug,
        BigDecimal price,
        String currency,
        boolean active,
        OffsetDateTime createdAt) {

    public static WishlistItemResponse from(
            WishlistItem item, Product product) {
        return new WishlistItemResponse(
                item.getId(),
                item.getProductId(),
                product.getName(),
                product.getSlug(),
                product.getPrice(),
                product.getCurrency(),
                product.isActive(),
                item.getCreatedAt());
    }
}
