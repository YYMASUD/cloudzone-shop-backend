package com.cloudzone.shopbackend.shipping;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Admin create body. {@code code} is restricted to a URL-safe alphabet because checkout
 * references it; {@code active} defaults to true when omitted.
 */
public record ShippingMethodCreateRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "[A-Za-z0-9_-]{3,50}",
                message = "must be 3-50 letters, digits, hyphens or underscores")
        String code,
        @NotBlank
        @Size(max = 150)
        String name,
        @Size(max = 255) String description,
        @NotNull @DecimalMin("0.00") BigDecimal flatRate,
        @Min(1) Integer estimatedDays,
        Boolean active) {
}