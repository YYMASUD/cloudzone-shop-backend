package com.cloudzone.shopbackend.auth;

import com.cloudzone.shopbackend.common.enums.AuthProvider;
import com.cloudzone.shopbackend.common.enums.Role;
import com.cloudzone.shopbackend.common.error.ApiException;
import com.cloudzone.shopbackend.common.security.AuthPrincipal;
import com.cloudzone.shopbackend.common.security.JwtProperties;
import com.cloudzone.shopbackend.common.security.JwtService;
import com.cloudzone.shopbackend.common.security.RsaPemReader;
import com.cloudzone.shopbackend.notification.MailService;
import com.cloudzone.shopbackend.user.User;
import com.cloudzone.shopbackend.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Credential operations behind {@code /auth}: registration, password login, social
 * sign-in, the refresh-token exchange, email verification and logout.
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
    private final MailService mailService;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final SocialTokenVerifier socialTokenVerifier;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            RsaPemReader pemReader,
            MailService mailService,
            EmailVerificationTokenRepository verificationTokenRepository,
            RevokedTokenRepository revokedTokenRepository,
            SocialTokenVerifier socialTokenVerifier) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.mailService = mailService;
        this.verificationTokenRepository = verificationTokenRepository;
        this.revokedTokenRepository = revokedTokenRepository;
        this.socialTokenVerifier = socialTokenVerifier;

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
        // A logged-out refresh token still verifies — revocation lives in the denylist,
        // not the signature. Checking jti here is what makes logout actually bite.
        if (jwt.getId() != null && revokedTokenRepository.existsByJti(jwt.getId())) {
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

    // ── social sign-in (Google / Apple) ───────────────────────────────────────

    /**
     * Signs a user in with a verified Google/Apple ID token, creating the account on
     * first use.
     *
     * <p>The token is verified by {@link SocialTokenVerifier} before anything else; from
     * then on this is an ordinary "look up by email, then mint a pair" — social accounts
     * are keyed by email so a person who already registered with a password keeps one
     * identity. A brand-new social account is marked verified only when the provider
     * says the email is verified; it never carries a password hash.
     */
    @Transactional
    public AuthTokenResponse socialLogin(SocialLoginRequest request) {
        SocialTokenVerifier.SocialIdentity identity =
                socialTokenVerifier.verify(request.provider(), request.idToken());

        User user = userRepository.findByEmailIgnoreCase(identity.email())
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(identity.email())
                        .passwordHash(null)                    // no local credential
                        .role(Role.CUSTOMER)
                        .authProvider(request.provider())
                        .enabled(true)
                        .emailVerified(identity.emailVerified())
                        .build()));

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

    // ── email verification (OTP) ───────────────────────────────────────────────

    /**
     * Issues a fresh 6-digit code and emails it to the caller.
     *
     * <p>Only the SHA-256 of the code is stored; the clear code exists solely in the
     * email. Resending supersedes the previous unused code (the confirm path checks the
     * most recent), so an attacker can't pin an earlier code. The send is async — this
     * endpoint returns as soon as the row is persisted.
     *
     * <p>Rate limiting on resend is intentionally not implemented yet; the OTP window
     * (10 min / 10^6 space) plus per-IP throttling at the edge is the guard for now.
     */
    @Transactional
    public void requestEmailVerification(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user_not_found",
                        "User no longer exists"));
        if (user.isEmailVerified()) {
            throw new ApiException(HttpStatus.CONFLICT, "email_already_verified",
                    "This email address is already verified");
        }
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        verificationTokenRepository.save(EmailVerificationToken.builder()
                .userId(userId)
                .codeHash(sha256(code))
                .expiresAt(OffsetDateTime.now().plus(OTP_TTL_MINUTES, ChronoUnit.MINUTES))
                .build());
        mailService.send(user.getEmail(), "Your verification code",
                "Your shop-backend verification code is " + code
                        + ".\nIt expires in " + OTP_TTL_MINUTES + " minutes.");
    }

    /**
     * Confirms the emailed code and flips {@code email_verified}.
     *
     * <p>A wrong/expired/used code is a uniform {@code invalid_code} 400 — no oracle
     * distinguishing "no code issued" from "wrong code".
     */
    @Transactional
    public void confirmEmail(Long userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user_not_found",
                        "User no longer exists"));
        if (user.isEmailVerified()) {
            throw new ApiException(HttpStatus.CONFLICT, "email_already_verified",
                    "This email address is already verified");
        }
        EmailVerificationToken token = verificationTokenRepository
                .findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId)
                .filter(t -> t.getExpiresAt().isAfter(OffsetDateTime.now()))
                .filter(t -> t.getCodeHash().equals(sha256(code)))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "invalid_code",
                        "The verification code is wrong or has expired"));
        token.setUsedAt(OffsetDateTime.now());
        user.setEmailVerified(true);
    }

    // ── logout / token revocation ─────────────────────────────────────────────

    /**
     * Revokes the presented refresh token by recording its {@code jti} in the denylist.
     *
     * <p>The token must still verify (signature + refresh type); we don't allow revoking
     * arbitrary jtis because that would let a caller DoS other sessions by guessing ids.
     * Revoking an already-revoked token is idempotent — a second logout on the same
     * credential is a no-op rather than a duplicate-row error.
     */
    @Transactional
    public void logout(LogoutRequest request) {
        Jwt jwt;
        try {
            jwt = refreshDecoder.decode(request.refreshToken());
        } catch (JwtException e) {
            // An already-dead credential needs no revocation; logout stays a 204 so the
            // response doesn't reveal whether the presented token was ever valid.
            return;
        }
        AuthPrincipal principal;
        try {
            principal = jwtService.toPrincipal(jwt);
        } catch (IllegalStateException e) {
            return;
        }
        if (principal.tokenType() != AuthPrincipal.TokenType.REFRESH || jwt.getId() == null) {
            return;
        }
        if (!revokedTokenRepository.existsByJti(jwt.getId())) {
            revokedTokenRepository.save(RevokedToken.builder()
                    .jti(jwt.getId())
                    .userId(principal.userId())
                    .expiresAt(OffsetDateTime.ofInstant(jwt.getExpiresAt(), java.time.ZoneOffset.UTC))
                    .revokedAt(OffsetDateTime.now())
                    .build());
        }
    }

    /** How long a verification code stays usable; bounds the brute-force window. */
    private static final int OTP_TTL_MINUTES = 10;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** SHA-256 hex of a clear code; the only form ever stored. */
    private static String sha256(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
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
