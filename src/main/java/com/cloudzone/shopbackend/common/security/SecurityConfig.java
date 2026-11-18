package com.cloudzone.shopbackend.common.security;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT resource-server setup.
 *
 * <p>Every request must present a valid {@code Authorization: Bearer} token except the
 * explicit permitAll list below. There are no sessions, no CSRF token and no form login:
 * the API is consumed by SPAs/mobile clients holding JWTs.
 *
 * <p>The decoder and the {@link JwtService} encoder share one RSA key pair, loaded from
 * {@link JwtProperties} — swapping to a file-based secret at runtime needs no code change.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final RsaPemReader pemReader;
    private final JwtProperties jwtProperties;
    private final CorsProperties corsProperties;

    public SecurityConfig(RsaPemReader pemReader, JwtProperties jwtProperties, CorsProperties corsProperties) {
        this.pemReader = pemReader;
        this.jwtProperties = jwtProperties;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        // Observability & docs — infrastructure surface, not business data
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Provider webhooks authenticate via their own signature, not a JWT
                        .requestMatchers("/api/webhooks/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Credential entry points — the only way to obtain the token
                        // that every other endpoint demands. GET /auth/me is NOT here:
                        // it requires a token on purpose (it proves the token path works).
                        .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/auth/refresh", "/auth/logout", "/auth/social").permitAll()
                        // Administrative surfaces — reachable only with an ADMIN token.
                        // Declared here rather than per-handler so the whole /api/admin
                        // namespace fails closed: adding a route there is always a
                        // deliberate decision about its authority.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Everything else requires a valid access token
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /** Reflects {@code app.cors.allowed-origins}; an empty list means no CORS headers at all. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Signs our own tokens. One RSA key pair for RS256, so a key rotation only has to
     * re-point {@code app.jwt.*-key-location}; no code change.
     */
    @Bean
    public JwtEncoder jwtEncoder() {
        RSAPublicKey publicKey = pemReader.readPublicKey(jwtProperties.publicKeyLocation());
        RSAPrivateKey privateKey = pemReader.readPrivateKey(jwtProperties.privateKeyLocation());
        return NimbusJwtEncoder.withKeyPair(publicKey, privateKey)
                .algorithm(JwtService.SIGNATURE_ALGORITHM)
                .build();
    }

    /**
     * BCrypt at cost 12 — the cost already baked into the dev seed hashes.
     * Injected wherever a password is hashed or checked, so a future cost bump or a
     * switch to Argon2 is a single edit.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Verifies signatures with our public key; rejects expired, foreign or refresh tokens. */
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(pemReader.readPublicKey(jwtProperties.publicKeyLocation()))
                .signatureAlgorithm(JwtService.SIGNATURE_ALGORITHM)
                .build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(JwtService.ISSUER),
                new AccessTokenValidator());
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /**
     * Maps the {@code roles} claim to {@code ROLE_*} {@link GrantedAuthority}s.
     * A custom claim is used instead of {@code scope}/{@code scp} because roles are our
     * authorization model; scopes would imply an OAuth2 client-granularity we don't have.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
        converter.setAuthoritiesClaimName(AuthPrincipal.CLAIM_ROLES);
        converter.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(converter);
        return jwtConverter;
    }
}
