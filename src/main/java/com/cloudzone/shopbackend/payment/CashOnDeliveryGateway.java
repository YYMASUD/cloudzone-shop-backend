package com.cloudzone.shopbackend.payment;

import com.cloudzone.shopbackend.common.enums.PaymentMethod;
import com.cloudzone.shopbackend.order.Order;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Cash-on-delivery "gateway".
 *
 * <p>There is no remote provider — capture is the act of recording that the courier
 * collected cash. This bean exists so the payment SPI stays uniform ({@code COD} is
 * resolved through the same {@link PaymentGatewayRegistry} as Stripe/Razorpay) and so
 * the lifecycle (capture → paid, refund → returned) is identical in shape to a gateway
 * method even though no money moved over a network.
 *
 * <p>The synthetic receipt keeps the same "a receipt exists once settled" invariant the
 * online providers maintain, which is what reconciliation and the payment tests assert.
 */
@Component
public class CashOnDeliveryGateway implements PaymentGateway {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.COD;
    }

    /**
     * Records cash collection. No network call — the confirmation is the audit record
     * that the courier settled the amount at hand-off.
     */
    @Override
    public GatewayConfirmation capture(Order order) {
        String ref = "cod-collect-" + order.getOrderNumber() + "-" + java.util.UUID.randomUUID();
        return new GatewayConfirmation(ref, ref, order.getTotal(),
                "{\"provider\":\"cod\",\"kind\":\"collect\",\"order\":\"" + order.getOrderNumber() + "\"}");
    }

    /**
     * Records a cash return (e.g. a refused delivery). Refund bookkeeping is the same
     * shape as a gateway refund so the payments row stays uniform.
     */
    @Override
    public GatewayConfirmation refund(Payment payment, BigDecimal amount, String reason) {
        String ref = "cod-refund-" + payment.getId() + "-" + java.util.UUID.randomUUID();
        return new GatewayConfirmation(ref, ref, amount,
                "{\"provider\":\"cod\",\"kind\":\"refund\",\"reason\":\""
                        + (reason == null ? "" : reason.replace("\"", "'")) + "\"}");
    }
}
