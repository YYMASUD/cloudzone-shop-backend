package com.cloudzone.shopbackend.coupon;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Public shape of a coupon. Shoppers only ever see {@code code}, {@code description}
 * and the discount mechanics; operational fields (redemption counters) are admin-only
 * and deliberately absent here.
 */
public record CouponResponse(
        Long id,
        String code,
        String description,
        CouponDiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minOrderTotal,
        OffsetDateTime expiresAt) {

    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDescription(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMinOrderTotal(),
                coupon.getExpiresAt());
    }
}