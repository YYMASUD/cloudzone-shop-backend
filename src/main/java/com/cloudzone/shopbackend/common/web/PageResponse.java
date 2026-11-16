package com.cloudzone.shopbackend.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * The stable JSON envelope for a slice of a larger collection.
 *
 * <p>Spring's {@link Page} is never serialised directly. Its JSON shape is an
 * implementation detail of Spring Data — it has changed between versions, it nests a
 * {@code Pageable} whose fields clients never need, and it is not a contract we control.
 * This record is the contract: renaming or reordering a field here is a deliberate,
 * reviewable API change.
 *
 * <p>{@code first}/{@code last} are derived rather than stored so a client never has to
 * compute {@code page * size + content.size() >= totalElements} itself — a formula that
 * silently breaks when the last page is partially filled, or when a concurrent write
 * shrinks the collection while the client is paging.
 *
 * @param <T> the row type
 */
public record PageResponse<T>(

        /** The rows on this page; empty when {@code page} overshoots {@code totalPages}. */
        List<T> content,

        /** Zero-based index of this page. */
        int page,

        /** Requested page size, after clamping to the allowed range. */
        int size,

        /** Total rows across all pages, not just this one. */
        long totalElements,

        /** Total number of pages; {@code 0} when there are no rows at all. */
        int totalPages,

        boolean first,

        boolean last) {

    public PageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }

    /** Adapts a Spring Data page to the public envelope. */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
