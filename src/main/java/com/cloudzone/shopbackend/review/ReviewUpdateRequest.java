package com.cloudzone.shopbackend.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

/**
 * Partial update of an own review. Explicit {@code @Null} guards keep the identity and
 * authorship fields out of the body entirely — the caller is the author by definition.
 */
public record ReviewUpdateRequest(

        @Null(message = "must not be provided")
        Long productId,

        @Null(message = "must not be provided")
        Long userId,

        @Null(message = "must not be provided")
        String authorName,

        @Min(value = 1, message = "must be at least 1")
        @Max(value = 5, message = "must be at most 5")
        Integer rating,

        @Size(max = 150, message = "must be at most 150 characters")
        String title,

        @Size(max = 2000, message = "must be at most 2000 characters")
        String body) {
}
