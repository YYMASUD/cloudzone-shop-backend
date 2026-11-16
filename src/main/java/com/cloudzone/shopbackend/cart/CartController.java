package com.cloudzone.shopbackend.cart;

import com.cloudzone.shopbackend.common.security.AuthPrincipal;
import com.cloudzone.shopbackend.common.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated shopper's cart.
 *
 * <p>Every route resolves the caller's id from the verified token rather than a path
 * parameter — a cart is always "mine", so there is no {@code /api/cart/{userId}} to
 * forge. The GET never creates a cart row; one is created lazily on the first add.
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;
    private final JwtService jwtService;

    public CartController(CartService cartService, JwtService jwtService) {
        this.cartService = cartService;
        this.jwtService = jwtService;
    }

    /** The caller's cart; an empty representation (not a 404) when it does not exist yet. */
    @GetMapping
    public CartResponse get(JwtAuthenticationToken authentication) {
        return cartService.cartFor(userId(authentication));
    }

    /** Adds units of a product, merging with an existing line for the same product. */
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse addItem(
            @Valid @RequestBody AddCartItemRequest request,
            JwtAuthenticationToken authentication) {
        return cartService.addItem(userId(authentication), request);
    }

    /** Sets one line's quantity to an absolute value; the line must be the caller's. */
    @PatchMapping("/items/{cartItemId}")
    public CartResponse updateItem(
            @PathVariable Long cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request,
            JwtAuthenticationToken authentication) {
        return cartService.updateItem(userId(authentication), cartItemId, request);
    }

    /** Removes one line; a foreign or unknown id is a 404. */
    @DeleteMapping("/items/{cartItemId}")
    public CartResponse removeItem(
            @PathVariable Long cartItemId,
            JwtAuthenticationToken authentication) {
        return cartService.removeItem(userId(authentication), cartItemId);
    }

    /** Empties the cart (idempotent — clearing an empty/absent cart is a no-op). */
    @DeleteMapping
    public CartResponse clear(JwtAuthenticationToken authentication) {
        cartService.clear(userId(authentication));
        return cartService.cartFor(userId(authentication));
    }

    private long userId(JwtAuthenticationToken authentication) {
        AuthPrincipal principal = jwtService.toPrincipal(authentication.getToken());
        return principal.userId();
    }
}
