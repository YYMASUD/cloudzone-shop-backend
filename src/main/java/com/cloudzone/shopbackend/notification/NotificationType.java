package com.cloudzone.shopbackend.notification;

/**
 * The notification kinds the {@code chk_notifications_type} CHECK constraint allows.
 * Stored as the enum name; order events arrive once an orders module exists.
 */
public enum NotificationType {
    ORDER_PLACED,
    ORDER_PAID,
    ORDER_SHIPPED,
    ORDER_CANCELLED,
    PROMOTION,
    SYSTEM;

    /** @return the matching type, or {@code null} when the raw value is unknown. */
    public static NotificationType from(String raw) {
        for (NotificationType type : values()) {
            if (type.name().equals(raw)) {
                return type;
            }
        }
        return null;
    }
}