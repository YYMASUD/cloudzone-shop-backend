package com.cloudzone.shopbackend.auth;

import com.cloudzone.shopbackend.common.enums.AuthProvider;
import com.cloudzone.shopbackend.common.enums.Role;
import com.cloudzone.shopbackend.common.error.ApiException;
import com.cloudzone.shopbackend.common.security.AuthPrincipal;
import com.cloudzone.shopbackend.common.security.JwtProperties;
import com.cloudzone.shopbackend.common.security.JwtService;
import com.cloudzone.shopbackend.common.security.RsaPemReader;
import com.cloudzone.shopbackend.user.User;
import com.cloudzone.shopbackend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Credential operations behind {@code /auth}: registration, password login, and the
 * refresh-token exchange.
 *
 * <p>Two deliberate security choices:
 * <ul>
 *   <li><strong>Credential failures are indistinguishable.</strong> A wrong email and a
 *       wrong password both surface as the same {@code invalid_credentials} 401, so the
 *       endpoint can't be used to enumerate registered accounts.</li>
 *   <li><strong>Refresh tokens never touch the API decoder.</strong> The resource-server
 *       decoder rejects {@code token_type=refresh} by design (see {@code AccessTokenValidator}).
 *       The exchange instead verifies the token on a second decoder that checks signature
 *       and expiry only, then asserts the {@code refresh} type itself — the same trusted
 *       path {@code JwtServiceTest} documents.</li>
 * </ul>
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final NimbusJwtDecoder refreshDecoder;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            RsaPemReader pemReader) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;

        // Verify a presented refresh token with our key + the default (timestamp) checks.
        // The API decoder's AccessTokenValidator is intentionally absent — that's what
        // would reject a refresh token, and we're the one endpoint meant to accept it.
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(pemReader.readPublicKey(jwtProperties.publicKeyLocation()))
                .signatureAlgorithm(JwtService.SIGNATURE_ALGORITHM)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefault());
        this.refreshDecoder = decoder;
    }

    /** Creates a LOCAL CUSTOMER account and returns its first token pair. */
    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "email_taken",
                    "An account with that email already exists");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(Role.CUSTOMER)
                .authProvider(AuthProvider.LOCAL)
                .enabled(true)
                .emailVerified(false)   // verified later via a confirm-email link
                .build();
        userRepository.save(user);

        return tokenPair(user);
    }

    /** Verifies credentials and returns a token pair; wrong-anything looks identical. */
    @Transactional(readOnly = true)
    public AuthTokenResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(this::invalidCredentials);

        // A social-login-only account has no password to check; it must log in that way.
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "account_disabled",
                    "This account has been disabled");
        }
        return tokenPair(user);
    }

    /**
     * Exchanges a valid refresh token for a fresh pair.
     *
     * <p>The presented token must verify, carry {@code token_type=refresh}, and still map
     * to an enabled account — the last two being checks the signature alone can't provide.
     */
    @Transactional(readOnly = true)
    public AuthTokenResponse refresh(RefreshRequest request) {
        Jwt jwt;
        try {
            jwt = refreshDecoder.decode(request.refreshToken());
        } catch (JwtException e) {
            throw invalidCredentials();   // expired / forged / wrong-key — all "invalid"
        }

        AuthPrincipal principal;
        try {
            principal = jwtService.toPrincipal(jwt);
        } catch (IllegalStateException e) {
            throw invalidCredentials();
        }
        if (principal.tokenType() != AuthPrincipal.TokenType.REFRESH) {
            // An access token is valid but is not a refresh credential — reject it here.
            throw invalidCredentials();
        }

        User user = userRepository.findById(principal.userId())
                .orElseThrow(this::invalidCredentials);
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "account_disabled",
                    "This account has been disabled");
        }
        return tokenPair(user);
    }

    /** Resolves the caller's own profile from the verified identity. */
    @Transactional(readOnly = true)
    public UserProfileResponse profileFor(AuthPrincipal principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user_not_found",
                        "User no longer exists"));
        return new UserProfileResponse(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getRole(), user.isEmailVerified());
    }

    private AuthTokenResponse tokenPair(User user) {
        String access = jwtService.issueAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refresh = jwtService.issueRefreshToken(user.getId(), user.getEmail(), user.getRole());
        return new AuthTokenResponse(access, refresh, "Bearer", jwtProperties.accessTokenExpirySeconds());
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials",
                "Invalid email or password");
    }
}
