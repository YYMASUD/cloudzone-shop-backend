package com.cloudzone.shopbackend.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body of {@code POST /api/inventory/reservations}.
 *
 * @param productId  product to hold; required
 * @param variantId  specific variant to hold; omit for the product's own stock
 * @param quantity   units to hold; must be positive
 * @param ttlMinutes hold lifetime; omit for an open-ended (admin-style) hold
 */
public record HoldStockRequest(
        @NotNull Long productId,
        Long variantId,
        @NotNull @Positive Integer quantity,
        @Positive Integer ttlMinutes) {
}
