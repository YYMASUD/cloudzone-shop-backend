package com.cloudzone.shopbackend.wishlist;

import com.cloudzone.shopbackend.common.security.AuthPrincipal;
import com.cloudzone.shopbackend.common.security.JwtService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's wishlist. Ownership always comes from the verified JWT; there is no
 * user-id path parameter that could expose another account's saved products.
 */
@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;
    private final JwtService jwtService;

    public WishlistController(WishlistService wishlistService, JwtService jwtService) {
        this.wishlistService = wishlistService;
        this.jwtService = jwtService;
    }

    /** Newest first; inactive products remain visible with {@code active=false}. */
    @GetMapping("/items")
    public List<WishlistItemResponse> list(JwtAuthenticationToken authentication) {
        return wishlistService.list(userId(authentication));
    }

    /** Adds an active product to the wishlist. */
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public WishlistItemResponse add(
            @Valid @RequestBody WishlistAddRequest request,
            JwtAuthenticationToken authentication) {
        return wishlistService.add(userId(authentication), request);
    }

    /** Removes one owner-scoped item; an unknown or foreign id is a 404. */
    @DeleteMapping("/items/{wishlistItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable Long wishlistItemId,
            JwtAuthenticationToken authentication) {
        wishlistService.remove(userId(authentication), wishlistItemId);
    }

    private long userId(JwtAuthenticationToken authentication) {
        AuthPrincipal principal = jwtService.toPrincipal(authentication.getToken());
        return principal.userId();
    }
}
