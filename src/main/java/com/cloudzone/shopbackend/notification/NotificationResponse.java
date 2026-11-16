package com.cloudzone.shopbackend.notification;

import java.time.OffsetDateTime;

/**
 * One feed entry as the caller sees it. {@code userId} is absent — the feed is always
 * the caller's own, so the owner is implicit.
 */
public record NotificationResponse(
        Long id,
        String type,
        String title,
        String body,
        String link,
        OffsetDateTime readAt,
        OffsetDateTime createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getBody(),
                notification.getLink(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}