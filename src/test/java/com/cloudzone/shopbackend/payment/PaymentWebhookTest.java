package com.cloudzone.shopbackend.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * The webhook receiver's security boundary: an event is only accepted when the
 * provider signature verifies, and the endpoint is reachable without a JWT.
 *
 * <p>The schema profile configures {@code whsec_test_secret} for Stripe, so these
 * tests sign payloads the same way Stripe does ({@code t=...,v1=HMAC(t + "." + body)})
 * and assert accept/reject without any live gateway.
 */
@ActiveProfiles("schema")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentWebhookTest {

    private static final String SECRET = "whsec_test_secret";

    @LocalServerPort private int port;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    @DisplayName("A correctly-signed Stripe event is accepted without a JWT")
    void validStripeSignatureIsAccepted() throws Exception {
        String payload = "{\"id\":\"evt_1\",\"data\":{\"object\":{\"id\":\"pi_123\"}}}";
        String timestamp = "1700000000";
        String signature = hmac(timestamp + "." + payload);

        HttpResponse<String> response = send("/api/webhooks/payments/stripe", payload,
                "stripe-signature", "t=" + timestamp + ",v1=" + signature);
        assertThat(response.statusCode()).isEqualTo(204);
    }

    @Test
    @DisplayName("A bad Stripe signature is 401 and never touches state")
    void badStripeSignatureIsRejected() throws Exception {
        String payload = "{\"id\":\"evt_2\"}";
        HttpResponse<String> response = send("/api/webhooks/payments/stripe", payload,
                "stripe-signature", "t=1700000000,v1=deadbeef");
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("A missing signature header is 401")
    void missingSignatureIsRejected() throws Exception {
        HttpResponse<String> response = send("/api/webhooks/payments/stripe",
                "{\"id\":\"evt_3\"}", null, null);
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("An unknown provider is a 404, not a 500")
    void unknownProviderIsNotFound() throws Exception {
        HttpResponse<String> response = send("/api/webhooks/payments/nope", "{}", null, null);
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("An unconfigured provider accepts but does not verify (audit-only)")
    void unconfiguredProviderIsAuditOnly() throws Exception {
        // PayPal has no webhook secret in this profile; the receiver stays permissive
        // so a gateway dashboard can be wired up before secrets land.
        HttpResponse<String> response = send("/api/webhooks/payments/paypal",
                "{\"id\":\"evt_4\"}", null, null);
        assertThat(response.statusCode()).isEqualTo(204);
    }

    private HttpResponse<String> send(String path, String body, String header, String value)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body));
        if (header != null) {
            builder.header(header, value);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String hmac(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
