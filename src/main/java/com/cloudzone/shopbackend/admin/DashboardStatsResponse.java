package com.cloudzone.shopbackend.admin;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The head-office snapshot the admin dashboard renders at a glance.
 *
 * <p>All values are computed server-side so the client never has to reconcile
 * definitions (what counts as "revenue", which orders count as "active"). Maps keyed by
 * enum name are deliberately loose: adding an {@code OrderStatus} or {@code Role} needs
 * no API change, only a new map entry the client can ignore.
 *
 * @param users          total accounts, and the enabled/customer/admin breakdowns
 * @param orders         per-status order counts (every {@code OrderStatus} key present)
 * @param revenue        gross revenue across revenue-bearing orders (PAID→DELIVERED)
 * @param revenueCurrency the ISO currency the totals are denominated in
 */
public record DashboardStatsResponse(
        UserStats users,
        Map<String, Long> orders,
        BigDecimal revenue,
        String revenueCurrency) {

    /** The user-facing counters. */
    public record UserStats(
            long total,
            long enabled,
            long customers,
            long admins) {}
}
