package com.cloudzone.shopbackend.catalog;

import org.springframework.data.domain.Sort;

/**
 * The orderings a catalogue listing may request.
 *
 * <p>An allowlist, not a pass-through: the client sends a stable token
 * ({@code price_asc}) and the service maps it to a known {@link Sort}. Binding a raw
 * property name from user input would let a caller sort by any mapping — including
 * columns that are not part of the response, which leaks ordering information — and
 * would produce queries no index covers.
 *
 * <p>Only {@code price_asc}/{@code price_desc} reference the same column, because a
 * storefront needs both directions; a separate {@code name_desc} would add a token
 * without a use case.
 */
public enum ProductSort {

    /**
     * Newest first — the default when the client sends no {@code sort}. Ties break on
     * {@code id}, so rows sharing a timestamp keep a stable, paged order.
     */
    NEWEST("newest", Sort.by(Sort.Direction.DESC, "createdAt")
            .and(Sort.by(Sort.Direction.DESC, "id"))),

    /** Alphabetical, A→Z, with an {@code id} tie-breaker for identical names. */
    NAME("name", Sort.by(Sort.Direction.ASC, "name")
            .and(Sort.by(Sort.Direction.ASC, "id"))),

    /** Cheapest first, alphabetical among equal prices. */
    PRICE_ASC("price_asc", Sort.by(Sort.Direction.ASC, "price")
            .and(Sort.by(Sort.Direction.ASC, "name"))
            .and(Sort.by(Sort.Direction.ASC, "id"))),

    /** Most expensive first, alphabetical among equal prices. */
    PRICE_DESC("price_desc", Sort.by(Sort.Direction.DESC, "price")
            .and(Sort.by(Sort.Direction.ASC, "name"))
            .and(Sort.by(Sort.Direction.ASC, "id")));

    private final String token;
    private final Sort sort;

    ProductSort(String token, Sort sort) {
        this.token = token;
        this.sort = sort;
    }

    /** The query-string token, e.g. {@code price_desc}. */
    public String token() {
        return token;
    }

    public Sort springSort() {
        return sort;
    }

    /**
     * Resolves a client token.
     *
     * <p>Parsed explicitly rather than relying on Spring's enum conversion: an unknown
     * value would otherwise surface through the generic {@code Exception} handler as a
     * 500, when the caller's mistake clearly deserves a 400.
     *
     * @return the matching ordering; {@link #NEWEST} when the token is absent, which is
     *         the documented storefront default
     * @throws IllegalArgumentException when the token is present but not in the allowlist
     */
    public static ProductSort fromToken(String token) {
        if (token == null || token.isBlank()) {
            return NEWEST;
        }
        String normalized = token.trim().toLowerCase();
        for (ProductSort value : values()) {
            if (value.token.equals(normalized)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unsupported sort '" + token
                + "'. Allowed: newest, name, price_asc, price_desc");
    }
}
