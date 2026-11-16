package com.cloudzone.shopbackend.coupon;

/**
 * How a {@link Coupon}'s {@code discountValue} is interpreted:
 * <ul>
 *   <li>{@code PERCENT} — a percentage off the order subtotal (0–100);</li>
 *   <li>{@code FIXED} — a flat amount off the order subtotal.</li>
 * </ul>
 * Stored as a string via {@code @Enumerated(EnumType.STRING)} (V4 CHECK constraint).
 */
public enum CouponDiscountType {
    PERCENT,
    FIXED
}