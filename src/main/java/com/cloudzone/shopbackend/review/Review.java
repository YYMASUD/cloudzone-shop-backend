package com.cloudzone.shopbackend.review;

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
 * One customer review of one product.
 *
 * <p>Like the wishlist, product/user links are plain {@code BIGINT} ids rather than
 * associations, but a review — unlike a bookmark — carries its own payload, so the
 * author's display name is snapshotted at write time: a review must survive the author
 * deleting their account ({@code ON DELETE CASCADE} on {@code reviews.user_id}) without
 * the product page losing attribution.
 *
 * <p>{@code uk_reviews_product_user} makes "review" an insert-once operation — the
 * service rejects the second insert with a 409 rather than relying on the constraint —
 * and {@code chk_reviews_rating} is additionally enforced in the bean-validation layer
 * so clients get a field-level 400 before the DB ever sees the row.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reviews_product_user",
                columnNames = {"product_id", "user_id"}))
public class Review extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "title")
    private String title;

    @Column(name = "body")
    private String body;

    /** Author display name, denormalised from {@code users} when the review was written. */
    @Column(name = "author_name", nullable = false)
    private String authorName;
}
