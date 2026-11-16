package com.cloudzone.shopbackend.common.enums;

/** Order lifecycle. */
public enum OrderStatus {
    PENDING,      // created, awaiting payment
    PAID,         // payment captured
    PROCESSING,   // being fulfilled
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED
}
