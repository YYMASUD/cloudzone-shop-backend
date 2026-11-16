package com.cloudzone.shopbackend.inventory;

/**
 * Lifecycle of a stock reservation.
 *
 * <ul>
 *   <li>{@code ACTIVE}    — stock is held; counted against availability</li>
 *   <li>{@code COMMITTED} — the hold was converted into a real order at checkout</li>
 *   <li>{@code RELEASED}  — the hold was given back (explicit release or expiry sweep)</li>
 * </ul>
 *
 * <p>Module-local on purpose: only the inventory ledger cares about this state machine.
 * Shared enums (order/payment status) live in {@code common.enums} because more than one
 * module reads them.
 */
public enum StockReservationStatus {
    ACTIVE,
    COMMITTED,
    RELEASED
}
