package com.cloudzone.shopbackend.payment;

import java.math.BigDecimal;

/**
 * What a gateway hands back after a capture or refund. Both fields are optional:
 * a provider that completed the operation but exposes no discrete receipt may
 * leave {@code receiptNumber} null, and {@code gatewayPayload} is the raw JSON
 * for support/audit when the gateway returns one.
 *
 * @param receiptNumber    human-facing receipt/charge id (e.g. Stripe charge id)
 * @param gatewayReference the gateway's internal object id (PaymentIntent, order id)
 * @param amount           the amount the gateway confirms moved
 * @param rawPayload       the gateway response body, for auditing/debugging
 */
public record GatewayConfirmation(
        String receiptNumber,
        String gatewayReference,
        BigDecimal amount,
        String rawPayload) {
}
