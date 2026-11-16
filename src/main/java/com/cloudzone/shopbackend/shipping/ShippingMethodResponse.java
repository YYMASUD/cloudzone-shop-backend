package com.cloudzone.shopbackend.shipping;

import java.math.BigDecimal;

/**
 * Shape of a shipping method. {@code active} is deliberately absent (matching
 * {@code BrandResponse}): the shopper listing already filters to active rows, and the
 * admin surface toggles visibility via PATCH with the effect visible in that listing.
 */
public record ShippingMethodResponse(
        Long id,
        String code,
        String name,
        String description,
        BigDecimal flatRate,
        Integer estimatedDays) {

    public static ShippingMethodResponse from(ShippingMethod method) {
        return new ShippingMethodResponse(
                method.getId(),
                method.getCode(),
                method.getName(),
                method.getDescription(),
                method.getFlatRate(),
                method.getEstimatedDays());
    }
}