package com.cloudzone.shopbackend.order;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** All orders newest first, for the admin order desk. */
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // ── dashboard aggregates ─────────────────────────────────────────────────

    /** Count of orders in a single lifecycle state. */
    long countByStatus(com.cloudzone.shopbackend.common.enums.OrderStatus status);

    /**
     * Gross revenue: the sum of {@code total} across all revenue-bearing orders —
     * PAID and beyond (PROCESSING, SHIPPED, DELIVERED) but not PENDING, CANCELLED
     * or REFUNDED. {@code COALESCE} keeps the result a zero, not a null, on an
     * empty table so the dashboard never has to guard a {@code BigDecimal} null.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM(o.total), 0) FROM Order o "
                    + "WHERE o.status IN (com.cloudzone.shopbackend.common.enums.OrderStatus.PAID, "
                    + "com.cloudzone.shopbackend.common.enums.OrderStatus.PROCESSING, "
                    + "com.cloudzone.shopbackend.common.enums.OrderStatus.SHIPPED, "
                    + "com.cloudzone.shopbackend.common.enums.OrderStatus.DELIVERED)")
    java.math.BigDecimal sumRevenue();
}
