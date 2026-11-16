package com.cloudzone.shopbackend.common.enums;

/** Payment lifecycle across all 4 gateways. */
public enum PaymentStatus {
    INITIATED,   // payment record created, gateway session not yet confirmed
    PENDING,     // awaiting gateway/customer action
    SUCCEEDED,   // funds captured
    FAILED,
    CANCELLED,
    REFUNDED
}
