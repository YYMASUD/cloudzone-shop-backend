package com.cloudzone.shopbackend.payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * The public shape of a payment attempt. Internal gateway ids stay internal — the
 * response exposes the human-facing {@code receiptNumber} and timestamps, not the
 * raw {@code gatewayReference}/{@code gatewayPayload} which are for server-side
 * reconciliation and audit only.
 */
public record PaymentResponse(
        Long id,
        Long orderId,
        String method,
        String status,
        BigDecimal amount,
        String currency,
        String receiptNumber,
        BigDecimal refundAmount,
        OffsetDateTime paidAt,
        OffsetDateTime refundedAt,
        OffsetDateTime createdAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getMethod().name(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getReceiptNumber(),
                payment.getRefundAmount(),
                payment.getPaidAt(),
                payment.getRefundedAt(),
                payment.getCreatedAt());
    }
}
