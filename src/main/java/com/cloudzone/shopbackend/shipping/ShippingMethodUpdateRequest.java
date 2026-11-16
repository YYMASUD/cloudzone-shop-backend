package com.cloudzone.shopbackend.shipping;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Admin partial update. Every field is optional: {@code null} means "leave as is".
 * {@code code} is deliberately immutable — it is the reference checkout stores against
 * orders, so renaming it would silently orphan purchase history.
 */
public record ShippingMethodUpdateRequest(
        @Size(max = 150) String name,
        @Size(max = 255) String description,
        @DecimalMin("0.00") BigDecimal flatRate,
        @Min(1) Integer estimatedDays,
        Boolean active) {
}