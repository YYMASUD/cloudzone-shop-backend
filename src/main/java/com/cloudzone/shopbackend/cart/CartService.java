package com.cloudzone.shopbackend.cart;

import com.cloudzone.shopbackend.catalog.Product;
import com.cloudzone.shopbackend.catalog.ProductRepository;
import com.cloudzone.shopbackend.common.error.ApiException;
import com.cloudzone.shopbackend.user.User;
import com.cloudzone.shopbackend.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cart use-cases for the authenticated shopper. Every method resolves the caller's
 * cart lazily (a cart row is created on first add — browsing an empty cart writes
 * nothing), and every mutation validates against live catalogue stock so a cart can
 * never promise more units than the shelf holds.
 *
 * <p>Price handling: {@code cart_items.unit_price} is a snapshot taken at add/refresh
 * time. Adding a line that already exists refreshes its snapshot to the current price,
 * so a stale quote cannot silently survive a catalogue price change.
 */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    /**
     * The caller's cart; an empty representation when no cart row exists yet. The read
     * path never creates — {@code carts.user_id} is {@code NOT NULL}, and a GET should
     * not write, so the empty response carries {@code id = null}.
     */
    @Transactional(readOnly = true)
    public CartResponse cartFor(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(() -> new CartResponse(
                        null, userId, null, List.of(), 0L, BigDecimal.ZERO));
    }

    /** Adds quantity units of a product, merging with an existing line when present. */
    @Transactional
    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        Product product = productRepository.findById(request.productId())
                .filter(Product::isActive)
                .orElseThrow(() -> unknownProduct(request.productId()));

        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository
                .findByCartIdAndProductId(cart.getId(), product.getId())
                .orElse(null);

        int newQuantity = request.quantity() + (item == null ? 0 : item.getQuantity());
        if (product.getStockQuantity() < newQuantity) {
            throw new ApiException(HttpStatus.CONFLICT, "insufficient_stock",
                    "Only %d unit(s) in stock for product %d".formatted(
                            product.getStockQuantity(), product.getId()));
        }

        if (item == null) {
            cartItemRepository.save(CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.quantity())
                    .unitPrice(product.getPrice())
                    .build());
        } else {
            item.setQuantity(newQuantity);
            item.setUnitPrice(product.getPrice()); // refresh the price snapshot
        }
        return toResponse(cart);
    }

    /** Sets a line's quantity (absolute, not additive); the line must be the caller's. */
    @Transactional
    public CartResponse updateItem(Long userId, Long cartItemId, UpdateCartItemRequest request) {
        CartItem item = ownedItem(userId, cartItemId);
        Product product = item.getProduct();
        if (product.getStockQuantity() < request.quantity()) {
            throw new ApiException(HttpStatus.CONFLICT, "insufficient_stock",
                    "Only %d unit(s) in stock for product %d".formatted(
                            product.getStockQuantity(), product.getId()));
        }
        item.setQuantity(request.quantity());
        item.setUnitPrice(product.getPrice());
        return toResponse(item.getCart());
    }

    /** Removes one line; a foreign or unknown id is a 404 (not enumerable). */
    @Transactional
    public CartResponse removeItem(Long userId, Long cartItemId) {
        CartItem item = ownedItem(userId, cartItemId);
        item.getCart().getItems().remove(item);
        return toResponse(item.getCart());
    }

    /** Empties the cart if it exists; clearing a never-created cart is a no-op. */
    @Transactional
    public void clear(Long userId) {
        cartRepository.findByUserId(userId)
                .ifPresent(cart -> cartItemRepository.deleteByCartId(cart.getId()));
    }

    // ── internals ──────────────────────────────────────────────────────────────

    /**
     * Get-or-create runs only on the mutation paths; the read path resolves
     * {@code findByUserId} first and never inserts. The user link is mandatory
     * ({@code carts.user_id NOT NULL}), so creation resolves the entity first.
     */
    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "user_not_found", "Unknown user: " + userId));
            return cartRepository.save(Cart.builder().user(user).build());
        });
    }

    private CartItem ownedItem(Long userId, Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
                .filter(i -> i.getCart().getUser().getId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "cart_item_not_found",
                        "No cart item %d in this user's cart".formatted(cartItemId)));
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItem> lines = cartItemRepository
                .findAllByCartIdOrderByCreatedAtAscIdAsc(cart.getId());
        List<CartItemResponse> items = lines.stream()
                .map(CartItemResponse::from)
                .toList();
        String currency = lines.isEmpty() ? null : lines.get(0).getProduct().getCurrency();
        BigDecimal subtotal = items.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long itemCount = items.stream().mapToLong(CartItemResponse::quantity).sum();
        return new CartResponse(
                cart.getId(), cart.getUser().getId(), currency, items, itemCount, subtotal);
    }

    private static ApiException unknownProduct(Long productId) {
        return new ApiException(HttpStatus.NOT_FOUND, "product_not_found",
                "Unknown or inactive product: " + productId);
    }
}


