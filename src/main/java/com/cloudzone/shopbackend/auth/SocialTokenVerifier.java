package com.cloudzone.shopbackend.auth;

import com.cloudzone.shopbackend.common.enums.AuthProvider;
import com.cloudzone.shopbackend.common.error.ApiException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

/**
 * Verifies third-party ID tokens (Google, Apple) against the provider's published JWKS.
 *
 * <p>This is the whole trust boundary for social sign-in: everything downstream works on
 * the {@link SocialIdentity} this returns, so the checks here are the only thing standing
 * between a forged token and an account. Three are mandatory — signature (via JWKS),
 * issuer, and audience (the token must have been minted for <em>our</em> client id).
 * An unverified email claim never authenticates anyone.
 *
 * <p>When a provider's client id is blank the verifier refuses rather than fetching keys:
 * an unconfigured deploy must fail the social route loudly, not fall back to trusting
 * whatever token arrives.
 *
 * <p>Decoders are built once per provider — {@code NimbusJwtDecoder} caches the JWK set
 * internally, so the network cost is paid on first verification only.
 */
@Service
public class SocialTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(SocialTokenVerifier.class);

    private final String googleClientId;
    private final String appleClientId;
    private final Map<AuthProvider, NimbusJwtDecoder> decoders = new ConcurrentHashMap<>();

    public SocialTokenVerifier(
            @Value("${app.oauth2.google.client-id:}") String googleClientId,
            @Value("${app.oauth2.apple.client-id:}") String appleClientId) {
        this.googleClientId = googleClientId;
        this.appleClientId = appleClientId;
    }

    /** The verified facts a social token asserts about its subject. */
    public record SocialIdentity(String email, String subject, boolean emailVerified) {}

    /**
     * Verifies the token and extracts the identity it asserts.
     *
     * @throws ApiException 400 for an unsupported provider or a token without an email,
     *         503 when the provider isn't configured, 401 when verification fails
     */
    public SocialIdentity verify(AuthProvider provider, String idToken) {
        Endpoint endpoint = endpointFor(provider);
        if (endpoint == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported_provider",
                    "Social login is only available for Google and Apple");
        }
        String clientId = clientIdFor(provider);
        if (clientId == null || clientId.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "social_login_not_configured",
                    provider + " sign-in is not configured on this server");
        }

        Jwt jwt;
        try {
            jwt = decoders.computeIfAbsent(provider, p -> buildDecoder(endpoint, clientId))
                    .decode(idToken);
        } catch (JwtException e) {
            log.debug("[{}] ID token verification failed: {}", provider, e.getMessage());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_id_token",
                    "The identity token could not be verified");
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "email_claim_required",
                    "The identity token did not include an email address");
        }
        return new SocialIdentity(email.trim().toLowerCase(), jwt.getSubject(),
                Boolean.TRUE.equals(jwt.getClaim("email_verified")));
    }

    /** Builds a decoder pinned to the provider's issuer and our client id. */
    private NimbusJwtDecoder buildDecoder(Endpoint endpoint, String clientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(endpoint.jwkSetUri()).build();
        // Issuer must be the provider; audience must name our app — a token minted for
        // someone else's client id must never authenticate a user here.
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(endpoint.issuer()),
                new JwtClaimValidator<Object>(JwtClaimNames.AUD, aud -> switch (aud) {
                    case List<?> list -> list.contains(clientId);
                    case String s -> clientId.equals(s);
                    case null, default -> false;
                })));
        return decoder;
    }

    private String clientIdFor(AuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> googleClientId;
            case APPLE -> appleClientId;
            default -> null;
        };
    }

    /** The JWKS + issuer each provider publishes. */
    private static Endpoint endpointFor(AuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> new Endpoint(
                    "https://www.googleapis.com/oauth2/v3/certs", "https://accounts.google.com");
            case APPLE -> new Endpoint(
                    "https://appleid.apple.com/auth/keys", "https://appleid.apple.com");
            default -> null;
        };
    }

    private record Endpoint(String jwkSetUri, String issuer) {}
}
