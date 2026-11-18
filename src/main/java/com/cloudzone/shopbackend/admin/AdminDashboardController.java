package com.cloudzone.shopbackend.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin dashboard: headline counters and gross revenue.
 *
 * <p>{@code /api/admin/**} is role-gated to ADMIN by {@code SecurityConfig}; this
 * controller adds no extra checks. Numbers are computed fresh — a dashboard is allowed
 * to be a few seconds stale in practice, but correctness first.
 */
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    public AdminDashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** Headline metrics: users, the order funnel, and gross revenue. */
    @GetMapping
    public DashboardStatsResponse stats() {
        return dashboardService.stats();
    }
}
