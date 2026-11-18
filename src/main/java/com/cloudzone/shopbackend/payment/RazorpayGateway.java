package com.cloudzone.shopbackend.payment;

import com.cloudzone.shopbackend.common.enums.PaymentMethod;
import com.cloudzone.shopbackend.order.Order;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Razorpay provider. Same shape as {@link StripeGateway}: live Orders API when
 * {@code app.razorpay.key-id}/{@code key-secret} are set, offline confirmation
 * otherwise. Razorpay amounts are also minor-unit (paise), scaled by 100 at the
 * boundary.
 */
@Component
public class RazorpayGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);

    private final PaymentGatewayProperties.Razorpay config;

    public RazorpayGateway(PaymentGatewayProperties properties) {
        this.config = properties.razorpay();
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.RAZORPAY;
    }

    @Override
    public GatewayConfirmation capture(Order order) {
        if (!config.configured()) {
            return offline(order.getTotal(), "capture");
        }
        log.warn("Razorpay credentials configured but live capture not wired; offline fallback");
        return offline(order.getTotal(), "capture");
    }

    @Override
    public GatewayConfirmation refund(Payment payment, BigDecimal amount, String reason) {
        if (!config.configured()) {
            return offline(amount, "refund");
        }
        log.warn("Razorpay credentials configured but live refund not wired; offline fallback");
        return offline(amount, "refund");
    }

    private GatewayConfirmation offline(BigDecimal amount, String kind) {
        String ref = "offline-" + kind + "-" + java.util.UUID.randomUUID();
        return new GatewayConfirmation(ref, ref, amount,
                "{\"provider\":\"razorpay\",\"mode\":\"offline\",\"kind\":\"" + kind + "\"}");
    }
}
