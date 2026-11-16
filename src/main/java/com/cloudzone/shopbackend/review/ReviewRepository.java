package com.cloudzone.shopbackend.review;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** One review per (product, user) — the service turns a repeat into a 409. */
    boolean existsByProductIdAndUserId(Long productId, Long userId);

    /** Storefront feed for one product, newest first. */
    Page<Review> findAllByProductIdOrderByCreatedAtDescIdDesc(Long productId, Pageable pageable);

    /** Owner-scoped lookup; a foreign id is indistinguishable from a missing one. */
    Optional<Review> findByIdAndUserId(Long id, Long userId);
}
