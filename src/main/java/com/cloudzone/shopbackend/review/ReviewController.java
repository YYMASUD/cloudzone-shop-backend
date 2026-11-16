package com.cloudzone.shopbackend.review;

import com.cloudzone.shopbackend.common.security.AuthPrincipal;
import com.cloudzone.shopbackend.common.security.JwtService;
import com.cloudzone.shopbackend.common.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
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
 * Shopper review surface. Product feeds are read-only catalogue data reachable with
 * any valid token; writes are always attributed to the caller from the verified JWT.
 */
@RestController
@RequestMapping("/api/products")
public class ReviewController {

    private final ReviewService reviewService;
    private final JwtService jwtService;

    public ReviewController(ReviewService reviewService, JwtService jwtService) {
        this.reviewService = reviewService;
        this.jwtService = jwtService;
    }

    /** One product's reviews, newest first; oversized {@code size} is clamped. */
    @GetMapping("/{productId}/reviews")
    public PageResponse<ReviewResponse> listForProduct(
            @PathVariable Long productId,
            Pageable pageable) {
        return reviewService.listForProduct(productId, pageable);
    }

    /** Writes the caller's review of a product; a repeat review is a 409. */
    @PostMapping("/{productId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(
            @PathVariable Long productId,
            @Valid @RequestBody ReviewCreateRequest request,
            JwtAuthenticationToken authentication) {
        return reviewService.create(userId(authentication), productId, request);
    }

    /** Edits one own review; nulls keep old values, foreign ids are a 404. */
    @PatchMapping("/reviews/{reviewId}")
    public ReviewResponse update(
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewUpdateRequest request,
            JwtAuthenticationToken authentication) {
        return reviewService.update(userId(authentication), reviewId, request);
    }

    /** Removes one own review; an unknown or foreign id is a 404. */
    @DeleteMapping("/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long reviewId,
            JwtAuthenticationToken authentication) {
        reviewService.delete(userId(authentication), reviewId);
    }

    private long userId(JwtAuthenticationToken authentication) {
        AuthPrincipal principal = jwtService.toPrincipal(authentication.getToken());
        return principal.userId();
    }
}
