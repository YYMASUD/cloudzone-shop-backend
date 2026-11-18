package com.cloudzone.shopbackend.user;

import com.cloudzone.shopbackend.common.BaseEntity;
import java.time.OffsetDateTime;

/**
 * One row of the caller's address book.
 *
 * <p>{@code isDefault} is echoed so a client can render the "ship here by default" marker;
 * at most one address per user carries it, enforced by {@link AddressService}.
 */
public record AddressResponse(
        Long id,
        String label,
        String recipientName,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country,
        boolean isDefault,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getLabel(),
                address.getRecipientName(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.isDefault(),
                address.getCreatedAt(),
                address.getUpdatedAt());
    }
}
