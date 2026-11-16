package com.cloudzone.shopbackend.inventory;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/inventory/reservations/{id}/commit}.
 *
 * @param orderId the order the hold becomes a committed allocation of
 */
public record CommitReservationRequest(@NotNull Long orderId) {
}
