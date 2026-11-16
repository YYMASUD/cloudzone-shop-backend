package com.cloudzone.shopbackend.common;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Base class for all JPA entities.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@code id} — BIGSERIAL surrogate primary key
 *   <li>{@code createdAt}/{@code updatedAt} — DB-managed audit timestamps
 *   <li>{@code version} — optimistic locking (prevents lost-update on concurrent writes)
 * </ul>
 *
 * <h2>Why the subclasses use {@code @Builder} and not {@code @SuperBuilder}</h2>
 *
 * <p>Every field declared here is infrastructure-managed: {@code id} is assigned by the
 * database identity sequence, timestamps by Hibernate's {@code @CreationTimestamp}/
 * {@code @UpdateTimestamp}, and {@code version} by the optimistic-locking machinery.
 * None of them should ever be supplied by application code when a new aggregate is
 * created, so keeping them out of the generated builder is the desired behaviour —
 * {@code User.builder().email(...).build()} compiles, while
 * {@code User.builder().id(1L).build()} does not. Failing at compile time beats a
 * silently mis-versioned row.
 *
 * <p>Consequently there are deliberately <strong>no setters</strong> on these fields either.
 * Updates flow through the subclass setters while the entity is managed inside a
 * transaction, which is what lets Hibernate detect dirty state and bump {@code version}.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Long getId() { return id; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
