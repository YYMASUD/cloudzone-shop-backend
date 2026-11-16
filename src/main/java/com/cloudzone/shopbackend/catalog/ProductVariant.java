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

/**
 * A sellable option of a {@link Product} — size, colour, storage, etc.
 *
 * <p>{@code price} is an optional override: when null the variant sells at the
 * parent's price. {@code options} is a free-form descriptor ("Size: M, Colour: Navy");
 * structured option modelling belongs to a later slice.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product_variants")
public class ProductVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Each variant is purchasable on its own, so it carries its own SKU. */
    @Column(name = "sku", nullable = false, unique = true, length = 64)
    private String sku;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "options", length = 500)
    private String options;

    /** Price override; null means "same as the parent product". */
    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "active", nullable = false)
    private boolean active;
}
