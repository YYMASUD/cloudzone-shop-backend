package com.cloudzone.shopbackend.wishlist;

import com.cloudzone.shopbackend.catalog.Product;
import com.cloudzone.shopbackend.catalog.ProductRepository;
import com.cloudzone.shopbackend.common.error.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User-scoped wishlist operations on the V5 {@code wishlist_items} table. */
@Service
public class WishlistService {

    private final WishlistItemRepository wishlistRepository;
    private final ProductRepository productRepository;

    public WishlistService(
            WishlistItemRepository wishlistRepository,
            ProductRepository productRepository) {
        this.wishlistRepository = wishlistRepository;
        this.productRepository = productRepository;
    }

    /** Lists the caller's saved items newest first, including now-inactive products. */
    @Transactional(readOnly = true)
    public List<WishlistItemResponse> list(Long userId) {
        return wishlistRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(item -> WishlistItemResponse.from(item, product(item.getProductId())))
                .toList();
    }

    /** Adds one active product; repeated adds yield 409 instead of silently duplicating. */
    @Transactional
    public WishlistItemResponse add(Long userId, WishlistAddRequest request) {
        Product product = productRepository.findById(request.productId())
                .filter(Product::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "product_not_found", "Unknown or inactive product: " + request.productId()));
        if (wishlistRepository.existsByUserIdAndProductId(userId, product.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "wishlist_item_exists",
                    "Product %d is already on this wishlist".formatted(product.getId()));
        }
        WishlistItem saved = wishlistRepository.save(WishlistItem.builder()
                .userId(userId)
                .productId(product.getId())
                .build());
        return WishlistItemResponse.from(saved, product);
    }

    /** Removes an item from the caller's wishlist; unknown/non-owned ids are a 404. */
    @Transactional
    public void remove(Long userId, Long wishlistItemId) {
        WishlistItem item = wishlistRepository.findById(wishlistItemId)
                .filter(candidate -> candidate.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "wishlist_item_not_found",
                        "No wishlist item %d for this user".formatted(wishlistItemId)));
        wishlistRepository.delete(item);
    }

    private Product product(Long productId) {
        return productRepository.findById(productId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "product_not_found",
                        "Unknown product: " + productId));
    }
}
