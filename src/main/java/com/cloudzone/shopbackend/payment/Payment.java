package com.cloudzone.shopbackend.payment;

import com.cloudzone.shopbackend.common.BaseEntity;
import com.cloudzone.shopbackend.common.enums.PaymentMethod;
import com.cloudzone.shopbackend.common.enums.PaymentStatus;
import com.cloudzone.shopbackend.order.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A payment attempt against an order via one of the supported gateways. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** Gateway-side identifier (PaymentIntent id, order id, etc.). */
    @Column(name = "gateway_reference", length = 255)
    private String gatewayReference;

    /** Raw gateway payload for auditing / debugging webhooks. */
    @Column(name = "gateway_payload", columnDefinition = "text")
    private String gatewayPayload;

    /** Human-facing receipt id returned by the gateway on capture. */
    @Column(name = "receipt_number", length = 255)
    private String receiptNumber;

    /** When the gateway confirmed the capture; null until SUCCEEDED. */
    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    /** When the refund was issued; null until REFUNDED. */
    @Column(name = "refunded_at")
    private OffsetDateTime refundedAt;

    /** Total refunded so far; CHECK-constrained to {@code [0, amount]}. */
    @Builder.Default
    @Column(name = "refund_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}
