package com.cloudzone.shopbackend.auth;

import com.cloudzone.shopbackend.common.enums.AuthProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A native-SDK sign-in: the client authenticates with Google/Apple's own SDK and sends
 * us the resulting ID token. We never see a password, and the token is verified
 * server-side against the provider's published keys before any account is touched.
 *
 * @param provider which identity provider issued the token; {@code LOCAL} is rejected
 * @param idToken  the provider's signed ID token ({@code JWT})
 */
public record SocialLoginRequest(
        @NotNull AuthProvider provider,
        @NotBlank String idToken) {
}
