package com.cloudzone.shopbackend.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/addresses}.
 *
 * <p>{@code country} is an ISO-3166-1 alpha-2 code, matching the {@code CHAR(2)} column —
 * carriers and tax engines need a strict code, not a free-text country name.
 * {@code isDefault} is a boxed {@link Boolean} so an omitted value means "not default"
 * rather than silently promoting the new address.
 */
public record AddressCreateRequest(

        @Size(max = 50) String label,

        @NotBlank @Size(max = 150) String recipientName,

        @NotBlank @Size(max = 255) String line1,

        @Size(max = 255) String line2,

        @NotBlank @Size(max = 100) String city,

        @Size(max = 100) String state,

        @Size(max = 20) String postalCode,

        @NotBlank
        @Pattern(regexp = "[A-Za-z]{2}", message = "must be a 2-letter ISO country code")
        String country,

        Boolean isDefault) {
}
