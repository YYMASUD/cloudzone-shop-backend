package com.cloudzone.shopbackend.notification;

import com.cloudzone.shopbackend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One entry in a user's in-app notification feed (V7 {@code notifications}).
 *
 * <p>Like the wishlist, {@code user_id} is a plain {@code BIGINT} rather than an
 * association; {@code ON DELETE CASCADE} (schema side) plus owner-scoped queries are all
 * the integrity the feed needs. {@code readAt} is {@code null} until the owner reads it.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** One of the {@link NotificationType} names; the DB CHECK enforces the same set. */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", length = 1000)
    private String body;

    /** Optional deep link the client can navigate to, e.g. {@code /orders/42}. */
    @Column(name = "link", length = 500)
    private String link;

    /** {@code null} while unread; set the first time the owner marks it read. */
    @Column(name = "read_at")
    private OffsetDateTime readAt;
}