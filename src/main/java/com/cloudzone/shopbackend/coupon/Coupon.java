package com.cloudzone.shopbackend.coupon;

import com.cloudzone.shopbackend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A discount code: {@code PERCENT} (value in [0, 100]) or {@code FIXED} (flat amount
 * off the order total). Redemption is counted in {@code times_redeemed} — the checkout
 * flow increments it inside its own transaction.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "coupons")
public class Coupon extends BaseEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private CouponDiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    /** Minimum order subtotal that must be met before this code applies. */
    @Column(name = "min_order_total", precision = 12, scale = 2)
    private BigDecimal minOrderTotal;

    /** Null means unlimited redemptions. */
    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(name = "times_redeemed", nullable = false)
    private int timesRedeemed;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "active", nullable = false)
    private boolean active;
}