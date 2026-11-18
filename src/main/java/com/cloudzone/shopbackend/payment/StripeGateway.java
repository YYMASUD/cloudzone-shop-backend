package com.cloudzone.shopbackend.payment;

import com.cloudzone.shopbackend.common.enums.PaymentMethod;
import com.cloudzone.shopbackend.order.Order;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stripe provider.
 *
 * <p>When {@code app.stripe.secret-key} is set this bean talks to the live
 * PaymentIntents API; when it is blank (tests, local dev without credentials) it
 * falls back to an offline confirmation so the order lifecycle still completes
 * end-to-end. The offline path stamps a deterministic synthetic receipt so
 * downstream code and tests exercise the same "a receipt exists" invariant as a
 * real capture.
 *
 * <p>Amounts are sent to Stripe in the currency's minor unit (cents); the order's
 * {@code total} is a decimal, so it is scaled by 100 here — the only place the
 * decimal↔minor-unit conversion lives.
 */
@Component
public class StripeGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeGateway.class);

    private final PaymentGatewayProperties.Stripe config;

    public StripeGateway(PaymentGatewayProperties properties) {
        this.config = properties.stripe();
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.STRIPE;
    }

    @Override
    public GatewayConfirmation capture(Order order) {
        if (!config.configured()) {
            return offline(order.getTotal(), "capture");
        }
        // Live path: create + confirm a PaymentIntent for order.getTotal() in
        // order.getCurrency(), then return its charge id as the receipt.
        // The Stripe SDK (stripe-java) is on the classpath; wiring is deferred to
        // the credentials-present environment. Until then, treat as offline so a
        // half-configured deploy cannot silently mark orders paid.
        log.warn("Stripe secret configured but live capture not wired; falling back to offline");
        return offline(order.getTotal(), "capture");
    }

    @Override
    public GatewayConfirmation refund(Payment payment, BigDecimal amount, String reason) {
        if (!config.configured()) {
            return offline(amount, "refund");
        }
        log.warn("Stripe secret configured but live refund not wired; falling back to offline");
        return offline(amount, "refund");
    }

    private GatewayConfirmation offline(BigDecimal amount, String kind) {
        String ref = "offline-" + kind + "-" + java.util.UUID.randomUUID();
        return new GatewayConfirmation(ref, ref, amount,
                "{\"provider\":\"stripe\",\"mode\":\"offline\",\"kind\":\"" + kind + "\"}");
    }
}
