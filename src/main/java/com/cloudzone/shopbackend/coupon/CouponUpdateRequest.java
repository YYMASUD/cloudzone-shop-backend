package com.cloudzone.shopbackend.coupon;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Admin partial update. Every field is optional: {@code null} means "leave as is".
 * Because of that, {@code minOrderTotal}/{@code expiresAt} cannot be cleared through
 * this endpoint — send an explicit value instead (a deliberate simplification until a
 * JSON-merge-patch style contract is needed).
 */
public record CouponUpdateRequest(
        @Size(max = 255) String description,
        CouponDiscountType discountType,
        @DecimalMin("0.00") BigDecimal discountValue,
        @DecimalMin("0.00") BigDecimal minOrderTotal,
        @Min(1) Integer maxRedemptions,
        OffsetDateTime expiresAt,
        Boolean active) {
}