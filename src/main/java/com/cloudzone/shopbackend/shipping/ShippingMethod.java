package com.cloudzone.shopbackend.shipping;

import com.cloudzone.shopbackend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An admin-managed shipping method: a flat rate charged per order. Checkout
 * references a method by its {@code code}; the rate is snapshotted into the order's
 * {@code shipping_cost} at purchase time, so later rate edits never touch old orders.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "shipping_methods")
public class ShippingMethod extends BaseEntity {

    /** URL-safe reference used by checkout ({@code STANDARD}, {@code EXPRESS}, ...). */
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    /** Flat rate per order; the DB CHECK rejects negatives and so does bean validation. */
    @Column(name = "flat_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal flatRate;

    /** Optional delivery estimate in days; {@code null} means "no published estimate". */
    @Column(name = "estimated_days")
    private Integer estimatedDays;

    /** Inactive methods disappear from the shopper listing but keep existing for orders. */
    @Column(name = "active", nullable = false)
    private boolean active;
}