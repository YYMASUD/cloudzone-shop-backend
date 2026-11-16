package com.cloudzone.shopbackend.cart;

import java.math.BigDecimal;
import java.util.List;

/**
 * The caller's cart. {@code currency} is the currency of the cart's products (null and
 * omitted for an empty cart — the app-wide Jackson {@code non_null} policy).
 */
public record CartResponse(
        Long id,
        Long userId,
        String currency,
        List<CartItemResponse> items,
        long itemCount,
        BigDecimal subtotal) {
}
