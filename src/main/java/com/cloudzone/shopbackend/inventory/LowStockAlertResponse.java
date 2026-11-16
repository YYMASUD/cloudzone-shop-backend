package com.cloudzone.shopbackend.inventory;

import java.time.OffsetDateTime;

public record LowStockAlertResponse(
        Long id,
        Long productId,
        Long variantId,
        int threshold,
        int currentStock,
        OffsetDateTime notifiedAt,
        OffsetDateTime acknowledgedAt) {

    public static LowStockAlertResponse from(LowStockAlert alert) {
        return new LowStockAlertResponse(
                alert.getId(), alert.getProductId(), alert.getVariantId(),
                alert.getThreshold(), alert.getCurrentStock(), alert.getNotifiedAt(),
                alert.getAcknowledgedAt());
    }
}
