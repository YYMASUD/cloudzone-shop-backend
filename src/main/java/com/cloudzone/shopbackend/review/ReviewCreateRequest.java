package com.cloudzone.shopbackend.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * The payload a shopper may set when writing a review. {@code rating} mirrors the DB
 * {@code chk_reviews_rating} check (1..5) so violations surface as a field-level 400.
 */
public record ReviewCreateRequest(

        @Min(value = 1, message = "must be at least 1")
        @Max(value = 5, message = "must be at most 5")
        int rating,

        @Size(max = 150, message = "must be at most 150 characters")
        String title,

        @Size(max = 2000, message = "must be at most 2000 characters")
        String body) {
}
