package com.cloudzone.shopbackend.common.security;

import com.cloudzone.shopbackend.common.enums.Role;
import java.time.Instant;
import java.util.List;

/**
 * Everything needed to authenticate a request from a token, without touching the database.
 *
 * <p>This is the object {@link JwtService} signs into the {@code access} token and the
 * object {@code SecurityConfig} rebuilds from verified claims. Keeping it a pure record
 * makes the claim contract testable and prevents accidental entity leakage into tokens.
 *
 * @param userId      {@code users.id}
 * @param email       login identity, also used as the JWT {@code sub}
 * @param role        application role; mapped to the {@code ROLE_} authority on the way out
 * @param tokenType   {@code access} or {@code refresh}
 * @param issuedAt    token creation instant
 * @param expiresAt   token expiry instant
 */
public record AuthPrincipal(
        long userId,
        String email,
        Role role,
        TokenType tokenType,
        Instant issuedAt,
        Instant expiresAt) {

    /** Claim holding the role name; read by {@code JwtGrantedAuthoritiesConverter}. */
    public static final String CLAIM_ROLES = "roles";

    /** Claim distinguishing a short-lived access token from a long-lived refresh token. */
    public static final String CLAIM_TOKEN_TYPE = "token_type";

    /** Claim carrying {@code users.id}, so endpoints never need to resolve by email. */
    public static final String CLAIM_USER_ID = "uid";

    /** Values for {@link #CLAIM_TOKEN_TYPE}. */
    public enum TokenType {
        ACCESS("access"),
        REFRESH("refresh");

        private final String value;

        TokenType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        /** @return the matching type, or {@code null} when the claim is absent/unknown. */
        public static TokenType from(String raw) {
            for (TokenType type : values()) {
                if (type.value.equals(raw)) {
                    return type;
                }
            }
            return null;
        }
    }

    /** Authorities in the {@code ROLE_*} form Spring Security's prefixing expects. */
    public List<String> authorities() {
        return List.of("ROLE_" + role.name());
    }
}
