package com.cloudzone.shopbackend.inventory;

import java.time.OffsetDateTime;

/**
 * The public shape of a stock reservation row. Ids only — the ledger is internal, so
 * clients correlate by product/variant id, never by entity graph.
 */
public record StockReservationResponse(
        Long id,
        Long productId,
        Long variantId,
        Long orderId,
        Long userId,
        int quantity,
        StockReservationStatus status,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt) {

    public static StockReservationResponse from(StockReservation reservation) {
        return new StockReservationResponse(
                reservation.getId(),
                reservation.getProductId(),
                reservation.getVariantId(),
                reservation.getOrderId(),
                reservation.getUserId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getCreatedAt());
    }
}
