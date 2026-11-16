package com.cloudzone.shopbackend.catalog;

/**
 * A top-level category as the catalogue screens need it.
 *
 * <p>The parent relationship is flattened to a slug rather than nested, so the response
 * stays a flat list a client can group by itself. Only active categories are ever
 * serialised — the repository query filters, so {@code active} would always be true and
 * is deliberately not a field.
 */
public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String description,
        String parentSlug) {

    static CategoryResponse from(Category category) {
        // parent is a LAZY association; the caller maps inside a transaction.
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getParent() == null ? null : category.getParent().getSlug());
    }
}
