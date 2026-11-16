package com.cloudzone.shopbackend.review;

import com.cloudzone.shopbackend.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Review moderation: paged newest-first across all products (optionally scoped to one
 * product via {@code ?productId=}) and hard deletes. Authority comes from SecurityConfig's
 * {@code /api/admin/**} gate — no per-controller role checks, matching the other modules.
 */
@RestController
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** Every review for moderation, newest first; oversized {@code size} is clamped. */
    @GetMapping
    public PageResponse<ReviewResponse> listAll(
            @RequestParam(required = false) Long productId,
            Pageable pageable) {
        return reviewService.listAll(productId, pageable);
    }

    /** Removes any review; an unknown id is a 404. */
    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long reviewId) {
        reviewService.deleteAsAdmin(reviewId);
    }
}
