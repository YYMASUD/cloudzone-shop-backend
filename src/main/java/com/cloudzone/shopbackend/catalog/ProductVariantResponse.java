package com.cloudzone.shopbackend.catalog;

import java.math.BigDecimal;

/**
 * A purchasable option of a product, as the variant list returns it.
 *
 * <p>{@code effectivePrice} is derived server-side: the variant's own {@code price} when
 * set, otherwise the parent product's. Clients never re-implement the fallback rule.
 * {@code inStock} is derived for the same reason as on {@link ProductResponse}.
 * Only active variants are ever returned, so {@code active} is not a field.
 */
public record ProductVariantResponse(
        Long id,
        String sku,
        String name,
        String options,
        BigDecimal price,
        BigDecimal effectivePrice,
        String currency,
        int stockQuantity,
        boolean inStock) {

    static ProductVariantResponse from(ProductVariant variant) {
        BigDecimal effective = variant.getPrice() != null
                ? variant.getPrice()
                : variant.getProduct().getPrice();
        return new ProductVariantResponse(
                variant.getId(),
                variant.getSku(),
                variant.getName(),
                variant.getOptions(),
                variant.getPrice(),
                effective,
                variant.getCurrency(),
                variant.getStockQuantity(),
                variant.getStockQuantity() > 0);
    }
}
