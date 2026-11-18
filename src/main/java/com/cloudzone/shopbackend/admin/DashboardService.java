package com.cloudzone.shopbackend.admin;

import com.cloudzone.shopbackend.common.enums.OrderStatus;
import com.cloudzone.shopbackend.common.enums.Role;
import com.cloudzone.shopbackend.order.OrderRepository;
import com.cloudzone.shopbackend.user.UserRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates the admin dashboard numbers.
 *
 * <p>Each counter is a single indexed count; the revenue is one {@code SUM} across the
 * revenue-bearing statuses. Read-only and un-paged — these are headline numbers, not a
 * drill-down (that lives on the per-module admin endpoints).
 */
@Service
public class DashboardService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public DashboardService(OrderRepository orderRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    /** The current headline metrics, computed fresh on each request. */
    @Transactional(readOnly = true)
    public DashboardStatsResponse stats() {
        // Order funnel: every lifecycle status present so the client can render a
        // complete bar even when a bucket is zero.
        Map<String, Long> orders = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            orders.put(status.name(), orderRepository.countByStatus(status));
        }

        BigDecimal revenue = orderRepository.sumRevenue();

        DashboardStatsResponse.UserStats users = new DashboardStatsResponse.UserStats(
                userRepository.count(),
                userRepository.countByEnabledTrue(),
                userRepository.countByRole(Role.CUSTOMER),
                userRepository.countByRole(Role.ADMIN));

        // Single-currency reporting is a known simplification: a multi-currency store
        // would need per-currency buckets, which the schema doesn't yet model.
        return new DashboardStatsResponse(users, orders, revenue, "USD");
    }
}
