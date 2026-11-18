package com.cloudzone.shopbackend.common.enums;

/** Supported payment gateways, plus offline cash-on-delivery. */
public enum PaymentMethod {
    STRIPE,
    RAZORPAY,
    PAYPAL,
    SSLCOMMERZ,
    /** Cash collected on delivery; the payment row is settled by the courier, not a gateway. */
    COD
}
