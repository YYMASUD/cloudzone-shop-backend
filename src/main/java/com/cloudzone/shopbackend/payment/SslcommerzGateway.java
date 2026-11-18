package com.cloudzone.shopbackend.payment;

import com.cloudzone.shopbackend.common.enums.PaymentMethod;
import com.cloudzone.shopbackend.order.Order;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SSLCommerz provider. The live path would initialise a session via the
 * store-id/store-password API and poll/validate the IPN; no dedicated Java SDK is
 * bundled, so like PayPal this is implemented against the same {@link
 * PaymentGateway} SPI and returns an offline confirmation until credentials and
 * the session client are wired.
 */
@Component
public class SslcommerzGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SslcommerzGateway.class);

    private final PaymentGatewayProperties.Sslcommerz config;

    public SslcommerzGateway(PaymentGatewayProperties properties) {
        this.config = properties.sslcommerz();
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.SSLCOMMERZ;
    }

    @Override
    public GatewayConfirmation capture(Order order) {
        if (!config.configured()) {
            return offline(order.getTotal(), "capture");
        }
        log.warn("SSLCommerz credentials configured but live capture not wired; offline fallback");
        return offline(order.getTotal(), "capture");
    }

    @Override
    public GatewayConfirmation refund(Payment payment, BigDecimal amount, String reason) {
        if (!config.configured()) {
            return offline(amount, "refund");
        }
        log.warn("SSLCommerz credentials configured but live refund not wired; offline fallback");
        return offline(amount, "refund");
    }

    private GatewayConfirmation offline(BigDecimal amount, String kind) {
        String ref = "offline-" + kind + "-" + java.util.UUID.randomUUID();
        return new GatewayConfirmation(ref, ref, amount,
                "{\"provider\":\"sslcommerz\",\"mode\":\"offline\",\"kind\":\"" + kind + "\"}");
    }
}
