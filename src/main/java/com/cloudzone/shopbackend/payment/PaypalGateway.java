package com.cloudzone.shopbackend.payment;

import com.cloudzone.shopbackend.common.enums.PaymentMethod;
import com.cloudzone.shopbackend.order.Order;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PayPal provider. PayPal has no Java SDK dependency in this build; the live path
 * would call the v2 Orders REST API with the OAuth client credentials. Until the
 * credentials are present and that client is wired, this bean returns an offline
 * confirmation — a stub in name only, since it implements the same {@link
 * PaymentGateway} contract as the real providers.
 */
@Component
public class PaypalGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(PaypalGateway.class);

    private final PaymentGatewayProperties.Paypal config;

    public PaypalGateway(PaymentGatewayProperties properties) {
        this.config = properties.paypal();
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.PAYPAL;
    }

    @Override
    public GatewayConfirmation capture(Order order) {
        if (!config.configured()) {
            return offline(order.getTotal(), "capture");
        }
        log.warn("PayPal credentials configured but live capture not wired; offline fallback");
        return offline(order.getTotal(), "capture");
    }

    @Override
    public GatewayConfirmation refund(Payment payment, BigDecimal amount, String reason) {
        if (!config.configured()) {
            return offline(amount, "refund");
        }
        log.warn("PayPal credentials configured but live refund not wired; offline fallback");
        return offline(amount, "refund");
    }

    private GatewayConfirmation offline(BigDecimal amount, String kind) {
        String ref = "offline-" + kind + "-" + java.util.UUID.randomUUID();
        return new GatewayConfirmation(ref, ref, amount,
                "{\"provider\":\"paypal\",\"mode\":\"offline\",\"kind\":\"" + kind + "\"}");
    }
}
