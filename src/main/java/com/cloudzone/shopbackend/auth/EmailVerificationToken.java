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
 * A single-use 6-digit email-verification code (V14 {@code email_verification_tokens}).
 *
 * <p>Only the SHA-256 of the code is stored — the clear code goes out over email and is
 * never persisted, so a table read can't mint a verification. {@code usedAt} enforces
 * single-use; {@code expiresAt} bounds the brute-force window (10 minutes, 10^6 space,
 * no throttling of the resend endpoint yet — see class comment on the service).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** SHA-256 hex digest of the 6-digit code, never the code itself. */
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /** Null until the code is confirmed; set the moment verification succeeds. */
    @Column(name = "used_at")
    private OffsetDateTime usedAt;
}
