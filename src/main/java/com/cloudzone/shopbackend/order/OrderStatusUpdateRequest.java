package com.cloudzone.shopbackend.order;

import com.cloudzone.shopbackend.common.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

/** Body of {@code PATCH /api/admin/orders/{id}/status}. */
public record OrderStatusUpdateRequest(
        @NotNull OrderStatus status) {
}
