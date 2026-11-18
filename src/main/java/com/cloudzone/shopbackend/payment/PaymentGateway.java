package com.cloudzone.shopbackend.payment;

import com.cloudzone.shopbackend.common.enums.PaymentMethod;
import com.cloudzone.shopbackend.order.Order;

/**
 * A payment provider: turns an order's outstanding balance into a captured (or
 * refunded) transaction against a gateway.
 *
 * <p>The SPI is deliberately synchronous and minimal. {@link #capture} moves the
 * order to paid; {@link #refund} returns funds. Each concrete provider is
 * responsible for translating these calls into its gateway's protocol (Stripe
 * PaymentIntents, Razorpay Orders, PayPal v2 Orders, SSLCommerz sessions) — when
 * the provider is not configured for the running environment the bean falls back
 * to an offline confirmation, so the order lifecycle is exercisable end-to-end in
 * tests and local dev without live credentials.
 *
 * <p>Implementations must be idempotent: a retried capture on an already-paid
 * order, or a refund that the gateway already processed, must not double-charge
 * or double-refund. The service layer guards this at the DB state level; the
 * provider additionally returns the gateway's own receipt so reconciliation can
 * confirm the money actually moved once, not twice.
 */
public interface PaymentGateway {

    /** Which {@link PaymentMethod} this provider services; the service routes by it. */
    PaymentMethod method();

    /**
     * Captures the order's {@code total} in its {@code currency}.
     *
     * @return the gateway's confirmation (receipt + reference + raw payload)
     */
    GatewayConfirmation capture(Order order);

    /**
     * Refunds a previously captured payment.
     *
     * @param payment the SUCCEEDED payment row to refund
     * @param amount  the amount to return (must be {@code > 0} and {@code <= amount})
     * @param reason  human-facing reason recorded on the payment
     * @return the gateway's confirmation
     */
    GatewayConfirmation refund(Payment payment, java.math.BigDecimal amount, String reason);
}
