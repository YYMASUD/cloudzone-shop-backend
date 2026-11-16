package com.cloudzone.shopbackend.inventory;

import com.cloudzone.shopbackend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An unacknowledged low-stock signal for a product or one of its variants. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "low_stock_alerts")
public class LowStockAlert extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "threshold", nullable = false)
    private int threshold;

    @Column(name = "current_stock", nullable = false)
    private int currentStock;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "notified_at", nullable = false)
    private OffsetDateTime notifiedAt;
}
