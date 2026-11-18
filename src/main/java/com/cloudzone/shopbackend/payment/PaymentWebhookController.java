package com.cloudzone.shopbackend.payment;

import com.cloudzone.shopbackend.common.enums.PaymentMethod;
import com.cloudzone.shopbackend.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider webhook receiver.
 *
 * <p>Gateways push asynchronous events (capture succeeded, refund settled, chargeback)
 * here; the synchronous {@code /pay} flow is only the optimistic path. The endpoint is
 * public (no JWT) but every request must carry a valid signature in a provider-specific
 * header — an unsigned or wrongly-signed event is rejected before it touches state.
 *
 * <p>Verification is per-provider and offline-tolerant: when a provider's
 * {@code webhook-secret} is blank the receiver accepts the event but treats it as an
 * audit-only append (it never mutates payment status), so a half-configured deploy can
 * still be wired up to a gateway dashboard without silently trusting unsigned input.
 */
@RestController
@RequestMapping("/api/webhooks/payments")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayProperties properties;

    public PaymentWebhookController(
            PaymentRepository paymentRepository,
            PaymentGatewayProperties properties) {
        this.paymentRepository = paymentRepository;
        this.properties = properties;
    }

    /**
     * Receives a provider event. {@code provider} is one of {@code stripe|razorpay|paypal|sslcommerz}.
     *
     * <p>Returns {@code 204} on accept (even audit-only) so the provider stops retrying;
     * a 401 on a bad signature tells the provider the secret is wrong.
     */
    @PostMapping("/{provider}")
    @Transactional
    public ResponseEntity<Void> receive(
            @PathVariable String provider,
            @RequestBody String payload,
            @RequestHeader Map<String, String> headers) {

        PaymentMethod method = parseProvider(provider);
        String secret = webhookSecretFor(method);

        // No configured secret → accept but do not trust: audit-only.
        if (secret == null || secret.isBlank()) {
            log.info("[{}] webhook received (no secret configured; audit-only) provider={}",
                    method, provider);
            return ResponseEntity.noContent().build();
        }

        if (!verify(method, secret, payload, headers)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_webhook_signature",
                    "Webhook signature verification failed for " + provider);
        }

        // Verified event: locate the payment it refers to via the gateway reference the
        // provider echoes back, then record the raw event for reconciliation. Status
        // mutation is intentionally NOT done here — the synchronous capture path already
        // drives PAID/REFUNDED; webhooks are the audit + reconciliation channel until a
        // provider-specific event model exists.
        String reference = extractReference(payload);
        if (reference != null) {
            paymentRepository.findByGatewayReference(reference).ifPresent(payment ->
                    payment.setGatewayPayload(
                            appendEvent(payment.getGatewayPayload(), payload)));
        } else {
            log.debug("[{}] verified webhook carried no recognisable reference", method);
        }
        return ResponseEntity.noContent().build();
    }

    // ── signature verification ────────────────────────────────────────────────

    /** Maps the URL segment to a method; an unknown provider is a 404, not a 500. */
    private PaymentMethod parseProvider(String provider) {
        try {
            return PaymentMethod.valueOf(provider.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "unknown_provider",
                    "No webhook endpoint for provider " + provider);
        }
    }

    /** The configured webhook secret for a method, or {@code null} when unset. */
    private String webhookSecretFor(PaymentMethod method) {
        return switch (method) {
            case STRIPE -> properties.stripe().webhookSecret();
            case RAZORPAY -> properties.razorpay().webhookSecret();
            default -> null;   // PayPal/SSLCommerz webhook secrets not yet modelled
        };
    }

    /**
     * Verifies the event signature. Stripe signs {@code t=<ts>,v1=<hmac>} over
     * {@code "<ts>.<payload>"}; Razorpay sends an {@code X-Razorpay-Signature} HMAC of the
     * raw body. Both are HMAC-SHA256 over the secret — a constant-time compare decides.
     */
    private boolean verify(PaymentMethod method, String secret,
            String payload, Map<String, String> headers) {
        return switch (method) {
            case STRIPE -> verifyStripe(secret, payload, headers.get("stripe-signature"));
            case RAZORPAY -> constantTimeEquals(
                    hmacSha256(secret, payload),
                    headers.get("x-razorpay-signature"));
            default -> false;
        };
    }

    /** Parses Stripe's {@code t=...,v1=...} header and recomputes the v1 signature. */
    private boolean verifyStripe(String secret, String payload, String sigHeader) {
        if (sigHeader == null) {
            return false;
        }
        String timestamp = null, expected = null;
        for (String part : sigHeader.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if ("t".equals(kv[0])) {
                timestamp = kv[1];
            } else if ("v1".equals(kv[0])) {
                expected = kv[1];
            }
        }
        if (timestamp == null || expected == null) {
            return false;
        }
        String signed = timestamp + "." + payload;
        return constantTimeEquals(hmacSha256(secret, signed), expected);
    }

    /** HMAC-SHA256 hex digest; an unkeyed-alg failure surfaces as a 500, never a pass. */
    private static String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    /** Constant-time hex compare so a signature check is not a timing oracle. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Pulls the gateway reference out of a provider event. Providers place it under
     * different keys ({@code data.object.id}, {@code payload.payment.entity.id}); rather
     * than hard-code one schema we scan for the first {@code "id"} token and treat it as
     * the reference — a best-effort hook until per-provider event models exist.
     */
    private static String extractReference(String payload) {
        int idx = payload.indexOf("\"id\"");
        if (idx < 0) {
            return null;
        }
        int colon = payload.indexOf(':', idx);
        int first = payload.indexOf('"', colon + 1);
        int second = first < 0 ? -1 : payload.indexOf('"', first + 1);
        return second < 0 ? null : payload.substring(first + 1, second);
    }

    /** Appends a new event to the stored audit payload without losing prior entries. */
    private static String appendEvent(String existing, String event) {
        return (existing == null || existing.isBlank() ? "" : existing + "\n---\n") + event;
    }
}
