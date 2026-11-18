package com.cloudzone.shopbackend.user;

import com.cloudzone.shopbackend.common.validation.NotBlankWhenPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /api/addresses/{id}}.
 *
 * <p>Every field is optional: {@code null} means "leave as is", matching
 * {@code ShippingMethodUpdateRequest}. Fields that are {@code NOT NULL} in the schema are
 * validated with {@code @NotBlank} <em>only when present</em>, so a patch that clears a
 * recipient name is rejected instead of reaching the database.
 */
public record AddressUpdateRequest(

        @Size(max = 50) String label,

        @NotBlankWhenPresent @Size(max = 150) String recipientName,

        @NotBlankWhenPresent @Size(max = 255) String line1,

        @Size(max = 255) String line2,

        @NotBlankWhenPresent @Size(max = 100) String city,

        @Size(max = 100) String state,

        @Size(max = 20) String postalCode,

        @Pattern(regexp = "[A-Za-z]{2}",
                message = "must be a 2-letter ISO country code")
        String country,

        Boolean isDefault) {
}
