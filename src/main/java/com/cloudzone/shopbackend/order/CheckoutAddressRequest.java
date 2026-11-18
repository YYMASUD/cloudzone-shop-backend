package com.cloudzone.shopbackend.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An inline address supplied at checkout instead of a saved {@code addresses} id.
 * Mirrors the {@code addresses} columns so the order can persist it as a real
 * {@code Address} row owned by the caller. Required fields match the DDL's
 * {@code NOT NULL} ones; the order module never mutates a user's saved defaults.
 *
 * @param recipientName who receives the parcel (DDL: {@code VARCHAR(150) NOT NULL})
 * @param line1         street address (DDL: {@code VARCHAR(255) NOT NULL})
 * @param line2         apartment, suite, … (optional)
 * @param city          city (DDL: {@code VARCHAR(100) NOT NULL})
 * @param state         state/province (optional)
 * @param postalCode    postal code (optional)
 * @param country       ISO-3166 alpha-2, exactly two letters (DDL: {@code VARCHAR(2) NOT NULL})
 */
public record CheckoutAddressRequest(
        @NotBlank @Size(max = 150) String recipientName,
        @NotBlank @Size(max = 255) String line1,
        @Size(max = 255) String line2,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 20) String postalCode,
        @NotBlank @Size(min = 2, max = 2) String country) {
}
