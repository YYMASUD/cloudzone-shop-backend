package com.cloudzone.shopbackend.review;

import java.time.OffsetDateTime;

/**
 * One review row as the API returns it. {@code userId} is deliberately absent —
 * authorship is exposed as the denormalised {@code authorName} snapshot, never the
 * raw account id.
 */
public record ReviewResponse(
        Long id,
        Long productId,
        int rating,
        String title,
        String body,
        String authorName,
        OffsetDateTime createdAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getProductId(),
                review.getRating(),
                review.getTitle(),
                review.getBody(),
                review.getAuthorName(),
                review.getCreatedAt());
    }
}
