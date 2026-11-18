package com.cloudzone.shopbackend.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByGatewayReference(String gatewayReference);

    /**
     * The most recent attempt against an order, for gateway retries and status checks.
     *
     * <p>Ties break on {@code id DESC}: rows inserted in the same transaction (same
     * millisecond, in tests and bulk retries alike) share a {@code created_at}, and an
     * un-tied {@code ORDER BY created_at DESC} is then non-deterministic — the same
     * stability convention {@code ProductSort} applies to catalogue listings.
     */
    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDescIdDesc(Long orderId);

    /** Count of payments in a status, for the admin dashboard's funnel. */
    long countByStatus(com.cloudzone.shopbackend.common.enums.PaymentStatus status);
}
