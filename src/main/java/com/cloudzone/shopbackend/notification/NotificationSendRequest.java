package com.cloudzone.shopbackend.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin broadcast body. {@code type} accepts any CHECK-allowed name; PROMOTION and
 * SYSTEM are the realistic ones while order events remain future work.
 */
public record NotificationSendRequest(
        @NotNull Long userId,
        @NotNull NotificationType type,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 1000) String body,
        @Size(max = 500) String link) {
}