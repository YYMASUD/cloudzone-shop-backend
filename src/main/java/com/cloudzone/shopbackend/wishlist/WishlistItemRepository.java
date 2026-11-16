package com.cloudzone.shopbackend.wishlist;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    /** Newest first; {@code id DESC} breaks same-timestamp ties (stable ordering). */
    List<WishlistItem> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserIdAndProductId(Long userId, Long productId);
}
