package com.cloudzone.shopbackend.inventory;

import com.cloudzone.shopbackend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One time-boxed hold on stock (e.g. created when a checkout starts).
 *
 * <p>This is a <strong>ledger</strong>, not an object graph: product/variant/user/order are
 * plain {@code BIGINT} ids rather than {@code @ManyToOne} associations. Availability math
 * sums millions of these rows by id; joining entities for rows that are never navigated
 * buys nothing and makes the batch expiry sweep needlessly lazy-load. Integrity is still
 * enforced — the DB foreign keys in {@code V3__inventory.sql} reject dangling ids.
 *
 * <p>Availability rule (enforced by {@link StockReservationService}): a product's sellable
 * stock is {@code products.stock_quantity} minus the sum of {@code ACTIVE} reservation
 * quantities for it. Reservations never decrement the product row itself, so a crashed
 * checkout self-heals via the expiry sweep instead of leaking stock.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "stock_reservations")
public class StockReservation extends BaseEntity {

    /** Set when the hold is committed to a checkout; null while it is a bare hold. */
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Null means the hold is against the product's own (unvarianted) stock. */
    @Column(name = "variant_id")
    private Long variantId;

    /** Owner of the hold; null only for anonymous/system holds. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StockReservationStatus status;

    /**
     * When the hold lapses. Null means "hold until explicitly released" (admin holds);
     * the periodic sweep demotes expired {@code ACTIVE} rows to {@code RELEASED}.
     */
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;
}
