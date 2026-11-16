package com.cloudzone.shopbackend.auth;

import com.cloudzone.shopbackend.common.security.AuthPrincipal;
import com.cloudzone.shopbackend.common.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authentication surface — the only unauthenticated business endpoints in the API.
 *
 * <p>{@code POST /auth/**} is whitelisted in {@code SecurityConfig}; {@code GET /auth/me}
 * deliberately is not, so it both returns the caller's profile and doubles as a live probe
 * that the token path works end-to-end.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    /** Creates a LOCAL CUSTOMER account. 201 to distinguish it from a plain login's 200. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthTokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** Verifies email/password and returns a token pair. */
    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Exchanges a refresh token for a fresh pair; the access-only API filter doesn't apply. */
    @PostMapping("/refresh")
    public AuthTokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    /**
     * The caller's own profile, resolved from the verified access token's {@code uid}.
     * Requires a valid access token — which is precisely what {@code GET /auth/me} proves.
     */
    @GetMapping("/me")
    public UserProfileResponse me(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        AuthPrincipal principal = jwtService.toPrincipal(jwt);
        return authService.profileFor(principal);
    }
}
