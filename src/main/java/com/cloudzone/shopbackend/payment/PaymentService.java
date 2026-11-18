package com.cloudzone.shopbackend.payment;

import com.cloudzone.shopbackend.common.enums.OrderStatus;
import com.cloudzone.shopbackend.common.enums.PaymentMethod;
import com.cloudzone.shopbackend.common.enums.PaymentStatus;
import com.cloudzone.shopbackend.common.error.ApiException;
import com.cloudzone.shopbackend.inventory.StockReservationService;
import com.cloudzone.shopbackend.notification.NotificationService;
import com.cloudzone.shopbackend.notification.NotificationType;
import com.cloudzone.shopbackend.order.Order;
import com.cloudzone.shopbackend.order.OrderRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment lifecycle: capture the outstanding balance on a PENDING order, and
 * refund a captured payment. All order-status transitions and their side-effects
 * (notification dispatch, restock) live here so they execute inside one
 * transaction — a failed capture must not leave the order half-paid.
 *
 * <p>Idempotency: capture is guarded by the payment's status — only an
 * {@code INITIATED} (or still-open {@code PENDING}) row can capture, so a retried
 * request on an already-SUCCEEDED payment is a 409, not a double charge. Refund is
 * guarded the same way plus the {@code refund_amount <= amount} CHECK.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGatewayRegistry gateways;
    private final StockReservationService stockReservationService;
    private final NotificationService notificationService;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            PaymentGatewayRegistry gateways,
            StockReservationService stockReservationService,
            NotificationService notificationService) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.gateways = gateways;
        this.stockReservationService = stockReservationService;
        this.notificationService = notificationService;
    }

    /**
     * Captures payment for the caller's order. Owner-only — a foreign order number
     * is a 404 (not enumerable). Only a PENDING order with an open
     * (INITIATED/PENDING) payment can be captured.
     */
    @Transactional
    public PaymentResponse captureForOwner(Long userId, String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .filter(o -> o.getUser().getId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "order_not_found",
                        "No order %s for this user".formatted(orderNumber)));
        return capture(order);
    }

    /**
     * Admin-facing capture: settles an order's outstanding payment by numeric id.
     * Used for COD collection (the courier marks cash received) and for manually
     * forcing a capture on an open online payment. Unlike {@link #captureForOwner}
     * this is not owner-scoped — the admin desk is role-gated, not enumerable-sensitive.
     */
    @Transactional
    public PaymentResponse captureForAdmin(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "order_not_found", "Unknown order: " + orderId));
        return capture(order);
    }

    private PaymentResponse capture(Order order) {
        Payment payment = paymentRepository
                .findFirstByOrderIdOrderByCreatedAtDescIdDesc(order.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "no_payment",
                        "Order %s has no payment attempt".formatted(order.getOrderNumber())));
        boolean cod = payment.getMethod() == PaymentMethod.COD;

        // COD is settled by the courier at hand-off, so it may capture on DELIVERED as
        // well as PENDING. Online methods still require PENDING — a paid order can't
        // re-capture, and a delivered gateway order is already PAID.
        boolean payable = cod
                ? order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.DELIVERED
                : order.getStatus() == OrderStatus.PENDING;
        if (!payable) {
            throw new ApiException(HttpStatus.CONFLICT, "order_not_payable",
                    "Order %s is %s and cannot be captured with %s"
                            .formatted(order.getOrderNumber(), order.getStatus(),
                                    cod ? "COD" : "an online method"));
        }
        if (payment.getStatus() != PaymentStatus.INITIATED
                && payment.getStatus() != PaymentStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "payment_not_open",
                    "Payment %d is %s and cannot be captured"
                            .formatted(payment.getId(), payment.getStatus()));
        }

        GatewayConfirmation confirmation = gateways.forMethod(payment.getMethod()).capture(order);

        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setReceiptNumber(confirmation.receiptNumber());
        payment.setGatewayReference(confirmation.gatewayReference());
        payment.setGatewayPayload(confirmation.rawPayload());
        payment.setPaidAt(OffsetDateTime.now());
        paymentRepository.save(payment);

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        notificationService.deliver(order.getUser().getId(), NotificationType.ORDER_PAID,
                "Payment received for order " + order.getOrderNumber(),
                "We confirmed your payment of " + order.getTotal() + " "
                        + order.getCurrency() + ". Your order is now being processed.",
                "/orders/" + order.getOrderNumber());

        return PaymentResponse.from(payment);
    }

    /**
     * Refunds a captured payment and returns the stock. Only a PAID order with a
     * SUCCEEDED payment can be refunded; a null {@code amount} refunds the full
     * captured total. Transitions the order to REFUNDED and restocks committed holds.
     */
    @Transactional
    public PaymentResponse refund(Long orderId, RefundRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "order_not_found", "Unknown order: " + orderId));
        if (order.getStatus() != OrderStatus.PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "order_not_refundable",
                    "Order %s is %s; only PAID orders can be refunded"
                            .formatted(order.getOrderNumber(), order.getStatus()));
        }
        Payment payment = paymentRepository
                .findFirstByOrderIdOrderByCreatedAtDescIdDesc(order.getId())
                .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "no_refundable_payment",
                        "Order %s has no captured payment to refund"
                                .formatted(order.getOrderNumber())));

        BigDecimal amount = request == null || request.amount() == null
                ? payment.getAmount()
                : request.amount();
        if (amount.signum() <= 0 || amount.compareTo(payment.getAmount()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_refund_amount",
                    "Refund amount must be > 0 and <= " + payment.getAmount());
        }
        String reason = request == null ? null : request.reason();

        GatewayConfirmation confirmation =
                gateways.forMethod(payment.getMethod()).refund(payment, amount, reason);

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundAmount(amount);
        payment.setRefundReason(reason);
        payment.setRefundedAt(OffsetDateTime.now());
        payment.setGatewayPayload(confirmation.rawPayload());
        paymentRepository.save(payment);

        stockReservationService.restockForOrder(order.getId());

        order.setStatus(OrderStatus.REFUNDED);
        orderRepository.save(order);

        notificationService.deliver(order.getUser().getId(), NotificationType.ORDER_CANCELLED,
                "Refund issued for order " + order.getOrderNumber(),
                "We refunded " + amount + " " + order.getCurrency()
                        + " to your original payment method.",
                "/orders/" + order.getOrderNumber());

        return PaymentResponse.from(payment);
    }
}
