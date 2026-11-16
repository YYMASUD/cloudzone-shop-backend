package com.cloudzone.shopbackend.catalog;

/**
 * A brand as the catalogue list returns it.
 *
 * <p>Only active brands are ever serialised — the repository filters, so {@code active}
 * would always be true and is deliberately not a field, matching {@link CategoryResponse}.
 */
public record BrandResponse(Long id, String name, String slug, String description) {

    static BrandResponse from(Brand brand) {
        return new BrandResponse(
                brand.getId(), brand.getName(), brand.getSlug(), brand.getDescription());
    }
}
