package com.cloudzone.shopbackend.wishlist;

import com.cloudzone.shopbackend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One product on one user's wishlist.
 *
 * <p>Like the inventory ledger, product/user links are plain {@code BIGINT} ids rather
 * than associations: the row is a pure bookmark, responses resolve display data on
 * demand, and {@code ON DELETE CASCADE} (schema side) plus owner-scoped queries are all
 * the integrity the table needs. {@code uk_wishlist_user_product} makes "add" an
 * insert-once operation — the service turns a duplicate into a 409.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "wishlist_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wishlist_user_product",
                columnNames = {"user_id", "product_id"}))
public class WishlistItem extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;
}
