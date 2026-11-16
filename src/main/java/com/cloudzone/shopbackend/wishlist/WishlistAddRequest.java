package com.cloudzone.shopbackend.wishlist;

import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/wishlist/items}. */
public record WishlistAddRequest(@NotNull Long productId) {
}
