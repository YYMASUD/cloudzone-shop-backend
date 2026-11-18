package com.cloudzone.shopbackend.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway credentials, bound from the {@code app.*} property tree that already
 * exists in {@code application.yaml}. Every field is optional at bind time: a
 * blank secret means that provider is not configured in this environment, which
 * the provider beans translate into a "capture/refund is a no-op offline"
 * fallback rather than a startup failure — tests and local dev must boot without
 * live gateway keys.
 */
@ConfigurationProperties(prefix = "app")
public record PaymentGatewayProperties(
        Stripe stripe,
        Razorpay razorpay,
        Paypal paypal,
        Sslcommerz sslcommerz) {

    public PaymentGatewayProperties {
        stripe = stripe == null ? new Stripe(null, null) : stripe;
        razorpay = razorpay == null ? new Razorpay(null, null, null) : razorpay;
        paypal = paypal == null ? new Paypal(null, null, null) : paypal;
        sslcommerz = sslcommerz == null ? new Sslcommerz(null, null, null) : sslcommerz;
    }

    /** Stripe secret key + webhook secret (webhook verification is a later slice). */
    public record Stripe(String secretKey, String webhookSecret) {
        public boolean configured() {
            return secretKey != null && !secretKey.isBlank();
        }
    }

    /** Razorpay key id/secret + webhook secret. */
    public record Razorpay(String keyId, String keySecret, String webhookSecret) {
        public boolean configured() {
            return keyId != null && !keyId.isBlank()
                    && keySecret != null && !keySecret.isBlank();
        }
    }

    /** PayPal OAuth client credentials + API base URL (sandbox vs live). */
    public record Paypal(String clientId, String clientSecret, String baseUrl) {
        public boolean configured() {
            return clientId != null && !clientId.isBlank()
                    && clientSecret != null && !clientSecret.isBlank();
        }
    }

    /** SSLCommerz store credentials + API base URL. */
    public record Sslcommerz(String storeId, String storePassword, String baseUrl) {
        public boolean configured() {
            return storeId != null && !storeId.isBlank()
                    && storePassword != null && !storePassword.isBlank();
        }
    }
}
