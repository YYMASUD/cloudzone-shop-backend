package com.cloudzone.shopbackend.common;

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
 * Stores idempotency keys for mutating endpoints (payments, order creation).
 *
 * <p>Extends {@link BaseEntity} for the standard id/audit columns. The
 * {@code @Version} column is harmless here — rows are insert-once and updated
 * only when the original response is cached back.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord extends BaseEntity {

    /** Client-supplied Idempotency-Key header. */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    /** Request fingerprint (method + path + user) to detect key reuse. */
    @Column(name = "request_fingerprint", length = 512)
    private String requestFingerprint;

    /** Cached HTTP status of the original response. */
    @Column(name = "response_status")
    private Integer responseStatus;

    /** Cached response body (JSON). */
    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;
}
