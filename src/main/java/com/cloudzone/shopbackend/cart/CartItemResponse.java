package com.cloudzone.shopbackend.cart;

import java.math.BigDecimal;

/** One line of the caller's cart. Prices are the snapshot stored with the line. */
public record CartItemResponse(
        Long id,
        Long productId,
        String name,
        String slug,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal) {

    static CartItemResponse from(CartItem item) {
        // product is LAZY; callers map inside the service transaction.
        return new CartItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getProduct().getSlug(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }
}
