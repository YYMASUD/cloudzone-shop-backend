package com.cloudzone.shopbackend.cart;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Body of {@code PATCH /api/cart/items/{cartItemId}} — quantity is the new absolute value. */
public record UpdateCartItemRequest(
        @NotNull @Positive Integer quantity) {
}
