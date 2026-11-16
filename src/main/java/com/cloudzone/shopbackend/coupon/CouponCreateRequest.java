package com.cloudzone.shopbackend.coupon;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Admin create body. {@code code} is restricted to a URL-safe alphabet because it is
 * also a path segment on {@code GET /api/coupons/{code}}; {@code active} defaults to
 * true when omitted.
 */
public record CouponCreateRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "[A-Za-z0-9_-]{3,50}",
                message = "must be 3-50 letters, digits, hyphens or underscores")
        String code,
        @Size(max = 255) String description,
        @NotNull CouponDiscountType discountType,
        @NotNull @DecimalMin("0.00") BigDecimal discountValue,
        @DecimalMin("0.00") BigDecimal minOrderTotal,
        @Min(1) Integer maxRedemptions,
        OffsetDateTime expiresAt,
        Boolean active) {
}