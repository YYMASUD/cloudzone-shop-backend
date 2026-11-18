package com.cloudzone.shopbackend.payment;

import com.cloudzone.shopbackend.common.enums.PaymentMethod;
import com.cloudzone.shopbackend.common.error.ApiException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Routes a {@link PaymentMethod} to its {@link PaymentGateway}. Built from the
 * auto-wired list of gateway beans so adding a provider is a single new class —
 * no registry edit. A method with no registered bean is a 501-equivalent (400
 * "unsupported" here), never a NullPointerException.
 */
@Component
public class PaymentGatewayRegistry {

    private final Map<PaymentMethod, PaymentGateway> gateways = new EnumMap<>(PaymentMethod.class);

    public PaymentGatewayRegistry(List<PaymentGateway> beans) {
        for (PaymentGateway gateway : beans) {
            gateways.put(gateway.method(), gateway);
        }
    }

    /** The gateway for a method; a method with no provider bean is a 400. */
    public PaymentGateway forMethod(PaymentMethod method) {
        PaymentGateway gateway = gateways.get(method);
        if (gateway == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported_payment_method",
                    "No payment provider registered for method " + method);
        }
        return gateway;
    }
}
