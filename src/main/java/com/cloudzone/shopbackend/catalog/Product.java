package com.cloudzone.shopbackend.catalog;

import com.cloudzone.shopbackend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A sellable product. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    /** Stock keeping unit — unique business identifier. */
    @Column(name = "sku", nullable = false, unique = true, length = 64)
    private String sku;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 280)
    private String slug;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** Always use BigDecimal for money; never double. */
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Optional original price for showing discounts. */
    @Column(name = "compare_at_price", precision = 12, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /** Optional brand; products may be unbranded, mirroring {@link #category}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    /** Object-storage key for the primary image (see MinIO config). */
    @Column(name = "image_key", length = 512)
    private String imageKey;

    @Column(name = "active", nullable = false)
    private boolean active;
}
