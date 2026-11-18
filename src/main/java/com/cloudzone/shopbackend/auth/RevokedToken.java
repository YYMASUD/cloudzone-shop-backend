package com.cloudzone.shopbackend.auth;

import com.cloudzone.shopbackend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One entry in the refresh-token denylist (V14 {@code revoked_tokens}).
 *
 * <p>Keyed by the token's {@code jti} so the refresh exchange can check "was this exact
 * credential logged out?" in O(1). Access tokens are not denied here — they expire in an
 * hour by design, and a denylist that large would outgrow the benefit. {@code expiresAt}
 * mirrors the token's own expiry so a periodic sweep can drop entries that can never
 * be presented again anyway.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "revoked_tokens")
public class RevokedToken extends BaseEntity {

    @Column(name = "jti", nullable = false, length = 64)
    private String jti;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private OffsetDateTime revokedAt;
}
