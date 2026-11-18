package com.cloudzone.shopbackend.payment;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Body of {@code POST /api/admin/orders/{orderNumber}/refund}. {@code amount} null
 * means "refund the captured total in full"; a positive value is a partial refund.
 */
public record RefundRequest(
        BigDecimal amount,
        @Size(max = 500) String reason) {
}
